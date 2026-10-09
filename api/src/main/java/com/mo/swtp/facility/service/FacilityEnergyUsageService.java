package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityGroup;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityEnergyUsageDto;
import com.mo.swtp.facility.dto.FacilityEnergyUsageSearchDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.dto.RawDataFacilitySumDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시설별 사용량 2번섹션 — 운영시설 전력 사용량 조회 서비스 (읽기 전용).
 *
 * <p>운영시설({@link FacilityGroup#OPERATION} 8종) 각각의 4지표를 조회기간·집계단위에 맞게 산출한다:
 * 순시전력(마지막 분 시설합 PWI) · 전력량(PWQ 적산 버킷 차분 합) · 최대전력(버킷 시설합 PWI MAX) ·
 * 최대전력 일시(발생 버킷 시작 시각). 전력 측정 대상은 운영시설 + 재귀 하위 시설의 모든 계측기 PWI/PWQ 태그
 * 분(分)별 합산이며, 각 태그는 최근접 운영시설 조상으로 귀속된다 (시설별사용량-2번섹션 PLAN1 §확정된 설계 결정).</p>
 *
 * <p>아키텍처: {@link FacilityOperatingStatusService} 의 "시설 × 태그 IN절 in-memory 집계" 패턴 미러링.
 * 시계열 테이블은 마스터와 JOIN 하지 않고(FK 금지 정책), Service 가 {@code 태그→운영루트} 매핑을 메모리로
 * 구성해 native SQL 에 {@code unnest} 평행 배열로 전달한다. SQL 호출은 시설 수와 무관하게 고정 — 롤업 BFS
 * (레벨당 1회) + 계측기 IN 1회 + 태그 IN 1회 + 집계 3회. 사이클 간 자산 자동 원용 금지 정합 — 구조만 미러링한
 * 신규 자산 (PLAN1 §아키텍처).</p>
 *
 * <p>전력량은 {@link RawDataRepository#findEnergyDeltaBuckets}(per-tag 버킷 차분) 를 재사용하고 태그별 음수
 * 차분(적산 카운터 리셋) 을 제외 후 운영루트로 합산한다 — {@link com.mo.swtp.opt.service.PeakEnergyTrendService}
 * 의 {@code validDeltaOrNull} 동형 복제 ({@code .claude/rules/ot-integration.md §3} PWQ 적산값 차분 정책).
 * 순시전력 마지막값·최대전력은 분(分)별 시설합을 SQL 에서 같은 {@code acq_dtm} 으로 SUM 해야 하므로
 * ({@code SUM(MAX) != MAX(SUM)}) 시설별 native 집계 2종을 신규 사용한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FacilityEnergyUsageService {

    /** 표출 대상 시설 종류 — {@link FacilityGroup#OPERATION} 8종 (FacilityType SSOT 파생, 하드코딩 회피). */
    private static final List<FacilityType> OPERATION_TYPES = Arrays.stream(FacilityType.values())
            .filter(t -> t.getGroup() == FacilityGroup.OPERATION)
            .toList();

    /** 합산 대상 태그 유형 — PWI(순시전력) · PWQ(적산전력량). */
    private static final Set<TagMeasurementType> TARGET_TAG_TYPES =
            EnumSet.of(TagMeasurementType.PWI, TagMeasurementType.PWQ);

    private final FacilityRepository facilityRepository;
    private final FacilityOperatingRollupResolver rollupResolver;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 운영시설별 전력 사용량 4지표를 조회한다.
     *
     * @param searchDto 집계단위·검색시작일·검색종료일 (1번섹션 파라미터)
     * @return 운영시설별 전력 사용량 목록 (disp_ord → facility_nm 정렬, 데이터 부재 시 지표 null)
     * @throws RestApiException INVALID_SEARCH_PERIOD — 집계단위·기간 검증 실패
     */
    public List<FacilityEnergyUsageDto> findEnergyUsage(FacilityEnergyUsageSearchDto searchDto) {
        if (!searchDto.isValid()) {
            throw new RestApiException(FacilityErrorCode.INVALID_SEARCH_PERIOD);
        }
        LocalDateTime start = searchDto.toStartDtm();
        LocalDateTime end = searchDto.toEndExclusiveDtm();
        String unit = searchDto.getInqUnit().getDateTruncUnit();

        List<Facility> operatingRoots = facilityRepository
                .findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(OPERATION_TYPES, YnType.Y);
        if (operatingRoots.isEmpty()) {
            return List.of();
        }

        Map<String, String> facilityToRoot = rollupResolver.resolveRootByFacility(operatingRoots);
        List<Instrument> instruments = instrumentRepository
                .findByFacilityFacilityIdInAndUseYn(new ArrayList<>(facilityToRoot.keySet()), YnType.Y);
        Map<String, String> instrumentToRoot = buildInstrumentToRoot(instruments, facilityToRoot);
        TagRouting routing = splitTagsByType(loadActiveTags(instruments), instrumentToRoot);

        Map<String, BigDecimal> latestMap = aggregateLatest(routing, start, end);
        Map<String, RawDataFacilitySumDto> peakMap = aggregatePeak(routing, start, end, unit);
        Map<String, BigDecimal> energyMap = aggregateEnergy(routing, start, end, unit);

        return operatingRoots.stream()
                .map(root -> assemble(root, latestMap, peakMap, energyMap))
                .toList();
    }

    /** 롤업 시설 집합의 활성 계측기에서 PWI/PWQ 태그만 로드 (종류 무관 — 펌프·전력계 등 모든 계측기). */
    private List<Tag> loadActiveTags(List<Instrument> instruments) {
        if (instruments.isEmpty()) {
            return List.of();
        }
        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(t -> TARGET_TAG_TYPES.contains(t.getTagSeCd()))
                .toList();
    }

    /** 계측기 → 운영루트 매핑. {@code instrument.getFacility().getFacilityId()} 는 프록시 식별자 접근(추가 쿼리 0). */
    private Map<String, String> buildInstrumentToRoot(
            List<Instrument> instruments, Map<String, String> facilityToRoot) {
        Map<String, String> map = new HashMap<>();
        for (Instrument instrument : instruments) {
            String root = facilityToRoot.get(instrument.getFacility().getFacilityId());
            if (root != null) {
                map.put(instrument.getInstrumentId(), root);
            }
        }
        return map;
    }

    /** PWI(태그+루트 평행 배열) · PWQ(태그+태그별 루트 맵) 로 분기. native unnest 입력·전력량 그룹핑 재료. */
    private TagRouting splitTagsByType(List<Tag> tags, Map<String, String> instrumentToRoot) {
        List<String> pwiTags = new ArrayList<>();
        List<String> pwiRoots = new ArrayList<>();
        List<String> pwqTags = new ArrayList<>();
        Map<String, String> pwqTagToRoot = new HashMap<>();
        for (Tag tag : tags) {
            String root = instrumentToRoot.get(tag.getInstrument().getInstrumentId());
            if (root == null) {
                continue;
            }
            if (tag.getTagSeCd() == TagMeasurementType.PWI) {
                pwiTags.add(tag.getTagSrlNo());
                pwiRoots.add(root);
            } else if (tag.getTagSeCd() == TagMeasurementType.PWQ) {
                pwqTags.add(tag.getTagSrlNo());
                pwqTagToRoot.put(tag.getTagSrlNo(), root);
            }
        }
        return new TagRouting(pwiTags, pwiRoots, pwqTags, pwqTagToRoot);
    }

    /** 순시전력 — 시설별 마지막 분 시설합 PWI (kW). 시설 1건당 1행 (DISTINCT ON). */
    private Map<String, BigDecimal> aggregateLatest(
            TagRouting routing, LocalDateTime start, LocalDateTime end) {
        return rawDataRepository
                .findFacilityLatestMinuteSumElpwr(routing.pwiTags(), routing.pwiRoots(), start, end).stream()
                .collect(Collectors.toMap(RawDataFacilitySumDto::facilityId, RawDataFacilitySumDto::value));
    }

    /** 최대전력 + 발생 시각 — 시설별 버킷 시설합 PWI MAX (kW). 시설 1건당 1행 (DISTINCT ON argmax). */
    private Map<String, RawDataFacilitySumDto> aggregatePeak(
            TagRouting routing, LocalDateTime start, LocalDateTime end, String unit) {
        return rawDataRepository
                .findFacilityBucketPeakElpwr(routing.pwiTags(), routing.pwiRoots(), start, end, unit).stream()
                .collect(Collectors.toMap(RawDataFacilitySumDto::facilityId, r -> r));
    }

    /** 전력량 — PWQ 태그별 버킷 차분(음수 제외) 을 운영루트로 합산 (kWh). */
    private Map<String, BigDecimal> aggregateEnergy(
            TagRouting routing, LocalDateTime start, LocalDateTime end, String unit) {
        Map<String, BigDecimal> energyByRoot = new HashMap<>();
        for (RawDataBucketDto bucket : rawDataRepository
                .findEnergyDeltaBuckets(routing.pwqTags(), start, end, unit)) {
            BigDecimal delta = validDeltaOrNull(bucket.aggrVal(), bucket.tagSrlNo(), bucket.baseDtm());
            if (delta == null) {
                continue;
            }
            String root = routing.pwqTagToRoot().get(bucket.tagSrlNo());
            if (root != null) {
                energyByRoot.merge(root, delta, BigDecimal::add);
            }
        }
        return energyByRoot;
    }

    /**
     * 버킷 차분값 유효성 — null 또는 음수(적산 리셋·롤오버)면 null 반환 + 음수는 WARN 로그.
     * {@link com.mo.swtp.opt.service.PeakEnergyTrendService#validDeltaOrNull} 동형 복제
     * (사이클 간 자산 자동 원용 금지 — 공통 추출은 3건 이상 누적 시 별도 ANALYZE).
     */
    private BigDecimal validDeltaOrNull(BigDecimal aggrVal, String tagSrlNo, LocalDateTime baseDtm) {
        if (aggrVal == null) {
            return null;
        }
        if (aggrVal.signum() < 0) {
            log.warn("음수 전력량 차분 버킷 제외 — tagSrlNo={}, baseDtm={}, aggrVal={}", tagSrlNo, baseDtm, aggrVal);
            return null;
        }
        return aggrVal;
    }

    /** 운영시설 1건의 4지표 조립 — 맵 lookup, 부재 시 null (0kW 와 측정 없음 구분). */
    private FacilityEnergyUsageDto assemble(
            Facility root,
            Map<String, BigDecimal> latestMap,
            Map<String, RawDataFacilitySumDto> peakMap,
            Map<String, BigDecimal> energyMap) {
        String rootId = root.getFacilityId();
        RawDataFacilitySumDto peak = peakMap.get(rootId);
        return FacilityEnergyUsageDto.of(
                rootId,
                root.getFacilityNm(),
                latestMap.get(rootId),
                energyMap.get(rootId),
                peak != null ? peak.value() : null,
                peak != null ? peak.dtm() : null);
    }

    /**
     * 태그 분기 결과 — PWI(평행 배열 — native unnest 입력) · PWQ(태그 리스트 + 태그별 루트 맵 — 전력량 그룹핑).
     *
     * @param pwiTags      PWI 태그 시리얼번호 (순서가 {@code pwiRoots} 와 평행)
     * @param pwiRoots     각 PWI 태그의 운영루트 ID ({@code pwiTags} 와 평행)
     * @param pwqTags      PWQ 태그 시리얼번호
     * @param pwqTagToRoot PWQ 태그 → 운영루트 ID 매핑 (전력량 버킷 차분 그룹핑용)
     */
    private record TagRouting(
            List<String> pwiTags,
            List<String> pwiRoots,
            List<String> pwqTags,
            Map<String, String> pwqTagToRoot) {
    }
}
