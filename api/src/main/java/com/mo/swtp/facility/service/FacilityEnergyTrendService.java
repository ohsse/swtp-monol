package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityGroup;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityEnergyTrendDto;
import com.mo.swtp.facility.dto.FacilityEnergyTrendSearchDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 시설별 사용량 5번섹션 — 운영시설 전력량 트렌드 조회 서비스 (읽기 전용).
 *
 * <p>운영시설({@link FacilityGroup#OPERATION} 8종) 각각의 전력량(PWQ 적산 버킷 차분 합, kWh)을 조회기간·집계단위
 * 버킷별로 보존해 시계열(트렌드)로 산출한다. 전력 측정 대상은 운영시설 + 재귀 하위 시설의 모든 계측기 PWQ 태그이며,
 * 각 태그는 최근접 운영시설 조상으로 귀속된다 (시설별사용량-5번섹션 PLAN1 §확정된 설계 결정).</p>
 *
 * <p>아키텍처: 2번섹션 {@link FacilityEnergyUsageService} 의 "시설 × 태그 IN절 in-memory 집계" 패턴 미러링하되
 * <strong>PWQ 버킷 보존 시계열</strong> 한 갈래만 다룬다 (순시전력 PWI·최대전력은 5번섹션 대상 외). 핵심 차이 —
 * 2번섹션이 {@link RawDataRepository#findEnergyDeltaBuckets} per-tag 버킷 차분을 {@code Map<root, BigDecimal>}
 * 단일값으로 뭉개는 반면, 5번섹션은 같은 결과를 {@code Map<root, TreeMap<baseDtm, BigDecimal>>} 버킷 보존으로
 * 합산해 시설별 시계열을 만든다. 따라서 <strong>신규 native query 0건</strong> (PLAN1 §핵심 발견). 시계열 테이블은
 * 마스터와 JOIN 하지 않고(FK 금지 정책), Service 가 {@code 태그→운영루트} 매핑을 메모리로 구성한다.
 * 사이클 간 자산 자동 원용 금지 정합 — 구조만 미러링한 신규 자산.</p>
 *
 * <p>전력량 차분은 태그별 음수 차분(적산 카운터 리셋·롤오버)을 제외 후 운영루트로 합산한다
 * ({@code .claude/rules/ot-integration.md §3} PWQ 적산값 차분 정책, GOOD 품질·{@code raw_val} 차분은
 * native query 가 강제). {@link RawDataRepository#findEnergyDeltaBuckets} 는 {@code acq_dtm} 범위 조건으로 월
 * RANGE 파티션 프루닝을 강제한다.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FacilityEnergyTrendService {

    /** 표출 대상 시설 종류 — {@link FacilityGroup#OPERATION} 8종 (FacilityType SSOT 파생, 하드코딩 회피). */
    private static final List<FacilityType> OPERATION_TYPES = Arrays.stream(FacilityType.values())
            .filter(t -> t.getGroup() == FacilityGroup.OPERATION)
            .toList();

    private final FacilityRepository facilityRepository;
    private final FacilityOperatingRollupResolver rollupResolver;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 운영시설별 전력량 트렌드를 조회한다.
     *
     * @param searchDto 집계단위·검색시작일·검색종료일 (1번섹션 파라미터)
     * @return 운영시설별 전력량 시계열 목록 (disp_ord → facility_nm 정렬, 데이터 부재 시 points 빈 리스트)
     * @throws RestApiException INVALID_SEARCH_PERIOD — 집계단위·기간 검증 실패
     */
    public List<FacilityEnergyTrendDto> findEnergyTrend(FacilityEnergyTrendSearchDto searchDto) {
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
        Map<String, String> pwqTagToRoot = collectPwqTagToRoot(instruments, instrumentToRoot);

        Map<String, TreeMap<LocalDateTime, BigDecimal>> trendByRoot =
                aggregateTrendByRoot(pwqTagToRoot, start, end, unit);
        return assembleSeriesList(operatingRoots, trendByRoot);
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

    /** 롤업 시설 집합의 활성 계측기에서 PWQ 태그만 수집 → {@code 태그 시리얼번호 → 운영루트} 맵 (전력량 그룹핑 재료). */
    private Map<String, String> collectPwqTagToRoot(
            List<Instrument> instruments, Map<String, String> instrumentToRoot) {
        if (instruments.isEmpty()) {
            return Map.of();
        }
        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        Map<String, String> pwqTagToRoot = new HashMap<>();
        for (Tag tag : tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y)) {
            if (tag.getTagSeCd() != TagMeasurementType.PWQ) {
                continue;
            }
            String root = instrumentToRoot.get(tag.getInstrument().getInstrumentId());
            if (root != null) {
                pwqTagToRoot.put(tag.getTagSrlNo(), root);
            }
        }
        return pwqTagToRoot;
    }

    /**
     * PWQ 태그별 버킷 차분(음수 제외)을 운영루트별 버킷 보존 시계열로 합산한다.
     * 2번섹션 {@code aggregateEnergy} 가 버킷을 뭉개는 것과 달리 {@code baseDtm} 키 {@link TreeMap} 으로 보존.
     */
    private Map<String, TreeMap<LocalDateTime, BigDecimal>> aggregateTrendByRoot(
            Map<String, String> pwqTagToRoot, LocalDateTime start, LocalDateTime end, String unit) {
        Map<String, TreeMap<LocalDateTime, BigDecimal>> trendByRoot = new HashMap<>();
        if (pwqTagToRoot.isEmpty()) {
            return trendByRoot;
        }
        List<String> pwqTags = new ArrayList<>(pwqTagToRoot.keySet());
        for (RawDataBucketDto bucket : rawDataRepository.findEnergyDeltaBuckets(pwqTags, start, end, unit)) {
            BigDecimal delta = validDeltaOrNull(bucket.aggrVal(), bucket.tagSrlNo(), bucket.baseDtm());
            if (delta == null) {
                continue;
            }
            String root = pwqTagToRoot.get(bucket.tagSrlNo());
            if (root == null) {
                continue;
            }
            trendByRoot.computeIfAbsent(root, k -> new TreeMap<>())
                    .merge(bucket.baseDtm(), delta, BigDecimal::add);
        }
        return trendByRoot;
    }

    /**
     * 버킷 차분값 유효성 — null 또는 음수(적산 리셋·롤오버)면 null 반환 + 음수는 WARN 로그.
     * 2번섹션 {@link FacilityEnergyUsageService} · {@code PeakEnergyTrendService} 에 이은 동형 복제
     * (사이클 간 자산 자동 원용 금지 — 공통 추출은 누적 사례 기반 별도 ANALYZE, 시설별사용량-5번섹션 ANALYZE1 안건 2).
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

    /** 운영루트 정렬 순서대로 시설별 시리즈 조립 (데이터 0 시설은 points 빈 리스트로 포함). */
    private List<FacilityEnergyTrendDto> assembleSeriesList(
            List<Facility> operatingRoots,
            Map<String, TreeMap<LocalDateTime, BigDecimal>> trendByRoot) {
        return operatingRoots.stream()
                .map(root -> assembleSeries(root, trendByRoot.get(root.getFacilityId())))
                .toList();
    }

    /** 운영시설 1건의 버킷 시계열을 {@code baseDtm} 오름차순 포인트 리스트로 조립 ({@link TreeMap} 순회). */
    private FacilityEnergyTrendDto assembleSeries(
            Facility root, TreeMap<LocalDateTime, BigDecimal> buckets) {
        List<FacilityEnergyTrendDto.EnergyTrendPoint> points = new ArrayList<>();
        if (buckets != null) {
            buckets.forEach((baseDtm, elcegVal) ->
                    points.add(FacilityEnergyTrendDto.EnergyTrendPoint.of(baseDtm, elcegVal)));
        }
        return FacilityEnergyTrendDto.of(root.getFacilityId(), root.getFacilityNm(), points);
    }
}
