package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.InqUnit;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityInstrumentEnergyUsageDto;
import com.mo.swtp.facility.dto.FacilityInstrumentEnergyUsageDto.InstrumentEnergyUsageItem;
import com.mo.swtp.facility.dto.FacilityInstrumentEnergyUsageSearchDto;
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
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설비별 사용량 5·6번섹션 — 설비별 누적 전력량·분포율 조회 서비스 (읽기 전용).
 *
 * <p>2번섹션에서 선택한 시설({@code facilityId})을 루트로 {@code parent_facility_id} self-FK 를 따라 재귀 하위
 * 트리 전체(루트 inclusive)를 수집하고, 그 시설들의 활성 계측기 중 PWQ(적산전력량) 태그 보유 계측기 각각의
 * {@code fromDt~toDt} 기간 누적 전력량(kWh, 5번섹션)과 분포율(%, 6번섹션)을 산정한다. 분포율은
 * {@code [설비 전력량 / 전체 설비 전력량] × 100} 이며 전체는 같은 재귀 하위 트리의 모든 PWQ 보유 계측기 합이다
 * (설비별사용량-5,6번섹션 PLAN1 §구현 방향 4).</p>
 *
 * <p>아키텍처는 같은 화면의 2·3번섹션 패턴 결합 — {@link FacilityPowerInstrumentService} 의 "앱 레벨 BFS 재귀
 * 하위 수집 + 시설·계측기·태그 IN절 일괄 조회" + {@link FacilityEnergyUsageService#aggregateEnergy} 의 "PWQ
 * 버킷 차분 합산"(그룹 키를 운영루트 → 계측기로 변경)이다. SQL 호출은 시설 수와 무관하게 고정 — 루트 1 + BFS
 * (레벨당 1) + 계측기 IN 1 + 태그 IN 1 + 버킷 1. 사이클 간 자산 자동 원용 금지 정합 — 구조만 미러링한 신규
 * 자산이다.</p>
 *
 * <p><strong>다중 PWQ 태그·다설비 합산</strong> — 한 계측기가 PWQ 태그를 다건 보유하면(예: ELCMTR 다채널 적산)
 * 동일 계측기로 버킷 차분을 합산한다. PWQ 보유 계측기는 기간 전력량이 0/무데이터라도 0kWh·0% 로 응답에 포함한다
 * (PLAN1 §가정 — 사용자 "0 으로 포함" 결정).</p>
 *
 * <p>도메인 룰 인용: {@code .claude/rules/ot-integration.md §3} — PWQ 는 Hold Last Value 미적용, GOOD only
 * {@code raw_val} 차분({@code MAX-MIN})이며 음수 차분(적산 리셋·롤오버) 버킷은 생략한다 (차분·필터 SQL 은
 * {@link RawDataRepository#findEnergyDeltaBuckets} 의 {@code §2.5} 면책 영역 — 본 서비스 추가 인용 불필요).
 * {@link #validDeltaOrNull}·{@link #collectSubtree}·{@link #findActiveFacilityOrThrow} 는 각각 3번째 동형
 * 복제이며, 공통 추출은 사본 3건 이상 누적 시 별도 ANALYZE 로 판정한다 ({@code .claude/rules/coding-discipline.md §3}
 * + ANALYZE1 wtp-backend-engineer 결론 — 본 사이클은 동형 복제 유지가 사용자 결정).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class FacilityInstrumentEnergyUsageService {

    /** 전력량(적산) 태그 유형 — PWQ. */
    private static final TagMeasurementType POWER_ENERGY_TYPE = TagMeasurementType.PWQ;

    /** 전력량 응답 단위. */
    private static final String UNIT_KWH = "kWh";

    /** 내부 전력량 버킷 단위 — 일(DAY) 고정 (사용자 미노출). 일별 양수 차분 합산으로 리셋·롤오버 강건. */
    private static final String BUCKET_UNIT = InqUnit.DAY.getDateTruncUnit();

    /** 재귀 하위 BFS 최대 깊이 — 순환·과도 깊이 방어 백스톱 (visited-set 이 1차 방어). */
    static final int MAX_DEPTH = 10;

    /** 분포율(%) 산정 분자 배율. */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 분포율 소수 자릿수 — 소수 첫째 자리 (6번섹션 도넛 차트 정합). */
    private static final int RATE_SCALE = 1;

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 루트 시설의 재귀 하위 트리 전체에서 설비별 누적 전력량·분포율을 조회한다.
     *
     * @param facilityId 루트 시설 ID (2번섹션에서 선택한 시설)
     * @param search     from~to 검색 조건 (1번섹션 파라미터)
     * @return 설비별 누적 전력량·분포율 응답 (계측기·PWQ 태그 부재 시 빈 래퍼)
     * @throws RestApiException {@link FacilityErrorCode#INVALID_SEARCH_PERIOD} — 기간 결측, from &gt; to, 396일 초과
     * @throws RestApiException {@link FacilityErrorCode#FACILITY_NOT_FOUND} — 미존재·비활성 루트 시설
     */
    public FacilityInstrumentEnergyUsageDto findInstrumentEnergyUsage(
            String facilityId, FacilityInstrumentEnergyUsageSearchDto search) {
        if (!search.isValid()) {
            throw new RestApiException(FacilityErrorCode.INVALID_SEARCH_PERIOD);
        }
        Facility root = findActiveFacilityOrThrow(facilityId);
        Map<String, Facility> facilityById = collectSubtree(root).stream()
                .collect(Collectors.toMap(Facility::getFacilityId, facility -> facility));

        List<Instrument> instruments = instrumentRepository
                .findByFacilityFacilityIdInAndUseYn(new ArrayList<>(facilityById.keySet()), YnType.Y);
        if (instruments.isEmpty()) {
            return emptyResult();
        }

        PwqRouting routing = collectPwqByInstrument(instruments);
        if (routing.pwqTags().isEmpty()) {
            return emptyResult();
        }

        Map<String, BigDecimal> energyByInstrument = aggregateEnergyByInstrument(routing, search);
        return assemble(routing.pwqInstruments(), energyByInstrument, facilityById);
    }

    /**
     * 활성 시설을 조회하거나 미존재·비활성 시 FACILITY_NOT_FOUND 예외를 던진다
     * ({@link FacilityPowerInstrumentService#findActiveFacilityOrThrow} 동형 복제 — 3번째 사본).
     */
    private Facility findActiveFacilityOrThrow(String facilityId) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
        if (facility.getUseYn() != YnType.Y) {
            throw new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND);
        }
        return facility;
    }

    /**
     * 루트를 포함한 재귀 하위 전체를 BFS 로 수집한다 — 레벨당 SQL 1회, visited-set 순환 방어 + depth 백스톱
     * ({@link FacilityPowerInstrumentService#collectSubtree} 동형 복제 — 3번째 사본).
     *
     * @param root 루트 시설
     * @return 루트 + 활성 재귀 하위 시설 전체 (방문 순서)
     */
    private List<Facility> collectSubtree(Facility root) {
        List<Facility> allNodes = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        allNodes.add(root);
        visited.add(root.getFacilityId());

        List<Facility> currentLevel = List.of(root);
        int depth = 0;
        while (!currentLevel.isEmpty() && depth < MAX_DEPTH) {
            List<String> parentIds = currentLevel.stream().map(Facility::getFacilityId).toList();
            List<Facility> children = facilityRepository.findByParentFacilityIdInAndUseYn(parentIds, YnType.Y);
            List<Facility> nextLevel = new ArrayList<>();
            for (Facility child : children) {
                if (visited.add(child.getFacilityId())) {
                    allNodes.add(child);
                    nextLevel.add(child);
                }
            }
            currentLevel = nextLevel;
            depth++;
        }
        return allNodes;
    }

    /**
     * 하위 트리 계측기 묶음의 활성 태그를 IN 절로 일괄 조회 → PWQ 만 필터 → 라우팅 정보로 구성한다.
     * PWQ 태그 시리얼번호 리스트(버킷 쿼리 입력) + 태그→계측기 맵(버킷 합산 그룹핑) + PWQ 보유 계측기 목록
     * (0 전력량 포함 대상)을 함께 담는다.
     */
    private PwqRouting collectPwqByInstrument(List<Instrument> instruments) {
        Map<String, Instrument> instrumentById = instruments.stream()
                .collect(Collectors.toMap(Instrument::getInstrumentId, instrument -> instrument));
        List<String> instrumentIds = new ArrayList<>(instrumentById.keySet());

        List<String> pwqTags = new ArrayList<>();
        Map<String, String> tagToInstrument = new HashMap<>();
        Map<String, Instrument> pwqInstrumentById = new LinkedHashMap<>();
        for (Tag tag : tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y)) {
            if (tag.getTagSeCd() != POWER_ENERGY_TYPE) {
                continue;
            }
            String instrumentId = tag.getInstrument().getInstrumentId();
            pwqTags.add(tag.getTagSrlNo());
            tagToInstrument.put(tag.getTagSrlNo(), instrumentId);
            pwqInstrumentById.putIfAbsent(instrumentId, instrumentById.get(instrumentId));
        }
        return new PwqRouting(pwqTags, tagToInstrument, new ArrayList<>(pwqInstrumentById.values()));
    }

    /**
     * PWQ 태그들의 버킷별 전력량 차분을 IN 절 단일 쿼리(일 버킷)로 조회한 뒤 태그의 계측기로 합산한다.
     * {@code null}·음수 차분(적산 리셋·롤오버) 버킷은 제외한다.
     */
    private Map<String, BigDecimal> aggregateEnergyByInstrument(
            PwqRouting routing, FacilityInstrumentEnergyUsageSearchDto search) {
        Map<String, BigDecimal> energyByInstrument = new HashMap<>();
        for (RawDataBucketDto bucket : rawDataRepository.findEnergyDeltaBuckets(
                routing.pwqTags(), search.toStartDtm(), search.toEndExclusiveDtm(), BUCKET_UNIT)) {
            BigDecimal delta = validDeltaOrNull(bucket.aggrVal(), bucket.tagSrlNo(), bucket.baseDtm());
            if (delta == null) {
                continue;
            }
            String instrumentId = routing.tagToInstrument().get(bucket.tagSrlNo());
            if (instrumentId != null) {
                energyByInstrument.merge(instrumentId, delta, BigDecimal::add);
            }
        }
        return energyByInstrument;
    }

    /**
     * 버킷 차분값 유효성 — null 또는 음수(적산 리셋·롤오버)면 null 반환 + 음수는 WARN 로그.
     * {@link FacilityEnergyUsageService#validDeltaOrNull} 동형 복제 — 3번째 사본
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

    /**
     * PWQ 보유 계측기 전체를 대상으로 설비별 항목을 조립한다. 전력량 맵 부재 계측기는 0kWh (0 포함 결정).
     * 전체 합({@code totalElceg})을 분모로 분포율을 산정하고, 시설 disp_ord → 계측기 disp_ord → 계측기명 정렬.
     */
    private FacilityInstrumentEnergyUsageDto assemble(
            List<Instrument> pwqInstruments,
            Map<String, BigDecimal> energyByInstrument,
            Map<String, Facility> facilityById) {
        BigDecimal total = pwqInstruments.stream()
                .map(instrument -> energyByInstrument.getOrDefault(instrument.getInstrumentId(), BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<InstrumentEnergyUsageItem> items = pwqInstruments.stream()
                .sorted(displayOrder(facilityById))
                .map(instrument -> toItem(instrument, energyByInstrument, facilityById, total))
                .toList();
        return FacilityInstrumentEnergyUsageDto.of(UNIT_KWH, total, items);
    }

    /** 계측기 1건 + 전력량·분포율을 응답 항목으로 조립 — 소속 시설명은 사전 수집 맵에서 조회. */
    private InstrumentEnergyUsageItem toItem(
            Instrument instrument,
            Map<String, BigDecimal> energyByInstrument,
            Map<String, Facility> facilityById,
            BigDecimal total) {
        Facility facility = facilityById.get(instrument.getFacility().getFacilityId());
        BigDecimal elceg = energyByInstrument.getOrDefault(instrument.getInstrumentId(), BigDecimal.ZERO);
        return InstrumentEnergyUsageItem.of(
                instrument.getInstrumentId(),
                instrument.getInstrumentNm(),
                instrument.getEquipType(),
                facility.getFacilityId(),
                facility.getFacilityNm(),
                elceg,
                computeRate(elceg, total));
    }

    /** 정렬 비교자 — 소속 시설 disp_ord → 계측기 disp_ord → 계측기명. 시설 disp_ord 는 사전 수집 맵에서 조회. */
    private Comparator<Instrument> displayOrder(Map<String, Facility> facilityById) {
        return Comparator
                .comparingInt((Instrument instrument) ->
                        facilityById.get(instrument.getFacility().getFacilityId()).getDispOrd())
                .thenComparingInt(Instrument::getDispOrd)
                .thenComparing(Instrument::getInstrumentNm);
    }

    /**
     * 분포율(%) 산정 — {@code value / total × 100}, 소수 첫째 자리 HALF_UP. {@code total} 0 시 분모 0 방어 → 0.0
     * ({@link com.mo.swtp.instrument.service.PumpCtrlHistoryService#computeRate} 미러링, 입력 타입만 BigDecimal).
     *
     * @param value 설비 전력량 (kWh)
     * @param total 전체 설비 전력량 합 (kWh)
     * @return 분포율 (%)
     */
    private BigDecimal computeRate(BigDecimal value, BigDecimal total) {
        if (total.signum() == 0) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return value.multiply(HUNDRED).divide(total, RATE_SCALE, RoundingMode.HALF_UP);
    }

    /** 계측기·PWQ 태그 부재 시 빈 래퍼 (전체합 0, 항목 빈 목록). */
    private FacilityInstrumentEnergyUsageDto emptyResult() {
        return FacilityInstrumentEnergyUsageDto.of(UNIT_KWH, BigDecimal.ZERO, List.of());
    }

    /**
     * PWQ 라우팅 정보 — 버킷 쿼리 입력(태그 리스트) + 합산 그룹핑(태그→계측기 맵) + 0 전력량 포함 대상(계측기 목록).
     *
     * @param pwqTags         PWQ 태그 시리얼번호 (버킷 차분 쿼리 입력)
     * @param tagToInstrument PWQ 태그 → 계측기 ID 매핑 (버킷 합산 그룹핑용)
     * @param pwqInstruments  PWQ 태그를 1건 이상 보유한 계측기 목록 (전력량 0 설비 포함 대상)
     */
    private record PwqRouting(
            List<String> pwqTags,
            Map<String, String> tagToInstrument,
            List<Instrument> pwqInstruments) {
    }
}
