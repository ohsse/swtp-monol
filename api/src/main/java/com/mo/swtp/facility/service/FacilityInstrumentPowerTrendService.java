package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendDto;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendDto.InstrumentPowerSeries;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendDto.PowerTrendPoint;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendSearchDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.dto.RawDataInstrumentSumDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 설비별 사용량 7번섹션 — 설비별 순시전력 트렌드 조회 서비스 (읽기 전용).
 *
 * <p>2번섹션에서 선택한 시설({@code facilityId}) 을 루트로 {@code parent_facility_id} self-FK 를 재귀 하위 트리
 * 전체(루트 inclusive)로 수집한 뒤, 그 시설들의 활성 계측기 중 순시전력 태그(PWI) 보유 설비별로 1번섹션
 * 기간({@code fromDt 00:00:00 ~ toDt 23:59:59})의 순시전력(kW) 1분 시계열을 멀티시리즈로 반환한다
 * (설비별사용량-7번섹션 PLAN1 §구현 방향).</p>
 *
 * <p>아키텍처 — "시설 → 계측기 → 태그 IN절 일괄 조회 + 인메모리 멤버십 필터" ({@link FacilityPowerInstrumentService}·
 * {@link FacilityInstrumentEnergyUsageService} 선례) + "앱 레벨 BFS 재귀 하위 수집" 의 결합. SQL 호출은 시설 수와
 * 무관하게 고정 — 루트 Facility 1 + BFS(레벨당 1회) + 계측기 IN 1 + 태그 IN 1 + 순시전력 시계열 1. 메서드 본문
 * 50줄 이내 + 추상화 3단 이내 ({@code .claude/rules/coding-discipline.md §2.1}).</p>
 *
 * <p><strong>다중 PWI 태그 동일시각 합산</strong> — 한 설비가 PWI 태그를 다건 보유하면 같은 {@code acq_dtm} 에서
 * 합산한다. 순시전력은 {@code SUM(MAX) != MAX(SUM)} 이므로 동시각 합이라야 의미를 가지며, 합산은
 * {@link RawDataRepository#findInstrumentMinuteSumElpwr} 의 {@code GROUP BY instrument_id, acq_dtm} 로 SQL 위임한다
 * (2번섹션 시설합 PWI 선례 동형). {@code quality_cd = 'GOOD'} 행만 {@code COALESCE(corr_val, raw_val)} 합산하여
 * BAD/UNCERTAIN·결측을 제외한다 — PWI 는 Hold Last Value 미적용 ({@code .claude/rules/ot-integration.md §3}).</p>
 *
 * <p>{@code equip_type_cd} 무필터 — PWI 태그 보유라는 물리 사실 기준 읽기 전용 조회이며, 자식 종류별 도메인 룰이
 * 분기하는 제어·평가 경로가 아니므로 {@code ot-integration.md §5} 필터 강제 룰 적용 범위 밖이다 (3·5·6번섹션 선례).
 * {@link #collectSubtree} · {@link #findActiveFacilityOrThrow} 는 {@link FacilityPowerInstrumentService} 의 동형
 * 복제다 (working code 비침습 — 공통 추출은 사본 누적 시 별도 ANALYZE, ANALYZE1 wtp-backend-engineer 결론 +
 * 사용자 결정 2026-06-09).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityInstrumentPowerTrendService {

    /** 재귀 하위 BFS 최대 깊이 — 순환·과도 깊이 방어 백스톱 (visited-set 이 1차 방어). */
    static final int MAX_DEPTH = 10;

    /** 순시전력 태그 유형 — PWI. */
    private static final TagMeasurementType POWER_TAG_TYPE = TagMeasurementType.PWI;

    /** 순시전력 응답 단위. */
    private static final String UNIT_KW = "kW";

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 선택 시설의 재귀 하위 트리 전체에서 PWI 태그 보유 설비별 순시전력 시계열을 조회한다.
     *
     * @param facilityId 루트 시설 ID (2번섹션에서 선택한 시설)
     * @param search     from~to 검색 조건
     * @return 설비별 순시전력 시계열 (PWI 보유 설비·데이터 부재 시 빈 series, 데이터 0 설비는 빈 points 포함)
     * @throws RestApiException INVALID_SEARCH_PERIOD — 기간 결측, from &gt; to, 31일 초과
     * @throws RestApiException FACILITY_NOT_FOUND — 존재하지 않거나 비활성 루트 시설
     */
    public FacilityInstrumentPowerTrendDto findPowerTrend(
            String facilityId, FacilityInstrumentPowerTrendSearchDto search) {
        if (!search.isValid()) {
            throw new RestApiException(FacilityErrorCode.INVALID_SEARCH_PERIOD);
        }
        Facility root = findActiveFacilityOrThrow(facilityId);
        Map<String, Facility> facilityById = collectSubtree(root).stream()
                .collect(Collectors.toMap(Facility::getFacilityId, facility -> facility));

        List<Instrument> instruments = instrumentRepository
                .findByFacilityFacilityIdInAndUseYn(new ArrayList<>(facilityById.keySet()), YnType.Y);
        if (instruments.isEmpty()) {
            return FacilityInstrumentPowerTrendDto.of(UNIT_KW, List.of());
        }

        Map<String, List<Tag>> pwiTagsByInstrument = loadPwiTagsByInstrument(instruments);
        List<Instrument> pwiInstruments = instruments.stream()
                .filter(instrument -> pwiTagsByInstrument.containsKey(instrument.getInstrumentId()))
                .sorted(displayOrder(facilityById))
                .toList();
        if (pwiInstruments.isEmpty()) {
            return FacilityInstrumentPowerTrendDto.of(UNIT_KW, List.of());
        }

        Map<String, List<PowerTrendPoint>> pointsByInstrument =
                queryPointsByInstrument(pwiInstruments, pwiTagsByInstrument, search);
        List<InstrumentPowerSeries> series = pwiInstruments.stream()
                .map(instrument -> toSeries(instrument, facilityById, pointsByInstrument))
                .toList();
        return FacilityInstrumentPowerTrendDto.of(UNIT_KW, series);
    }

    /**
     * 활성 시설을 조회하거나 미존재·비활성 시 FACILITY_NOT_FOUND 예외를 던진다
     * ({@link FacilityPowerInstrumentService#findActiveFacilityOrThrow} 동형 복제).
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
     * ({@link FacilityPowerInstrumentService#collectSubtree} 동형 복제).
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

    /** 계측기 묶음의 활성 태그를 IN 절로 일괄 조회 → PWI 태그만 필터 → 계측기 ID 로 그룹핑. */
    private Map<String, List<Tag>> loadPwiTagsByInstrument(List<Instrument> instruments) {
        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(tag -> tag.getTagSeCd() == POWER_TAG_TYPE)
                .collect(Collectors.groupingBy(tag -> tag.getInstrument().getInstrumentId()));
    }

    /**
     * PWI 태그→설비 평행 배열을 구성해 분(分) 시계열을 단일 쿼리로 조회한 뒤 계측기 ID 로 그룹핑한다.
     * SQL 이 {@code instrument_id ASC, acq_dtm ASC} 정렬을 보장하므로 {@link LinkedHashMap} 삽입 순서가 곧
     * 시각 오름차순이다.
     */
    private Map<String, List<PowerTrendPoint>> queryPointsByInstrument(
            List<Instrument> pwiInstruments,
            Map<String, List<Tag>> pwiTagsByInstrument,
            FacilityInstrumentPowerTrendSearchDto search) {
        List<String> tags = new ArrayList<>();
        List<String> instrumentIds = new ArrayList<>();
        for (Instrument instrument : pwiInstruments) {
            for (Tag tag : pwiTagsByInstrument.get(instrument.getInstrumentId())) {
                tags.add(tag.getTagSrlNo());
                instrumentIds.add(instrument.getInstrumentId());
            }
        }
        return rawDataRepository.findInstrumentMinuteSumElpwr(
                        tags, instrumentIds, search.toStartDtm(), search.toEndExclusiveDtm())
                .stream()
                .collect(Collectors.groupingBy(
                        RawDataInstrumentSumDto::instrumentId,
                        LinkedHashMap::new,
                        Collectors.mapping(
                                row -> PowerTrendPoint.of(row.dtm(), row.value()),
                                Collectors.toList())));
    }

    /** 정렬 비교자 — 소속 시설 disp_ord → 계측기 disp_ord → 계측기명. 시설 disp_ord 는 사전 수집 맵에서 조회. */
    private Comparator<Instrument> displayOrder(Map<String, Facility> facilityById) {
        return Comparator
                .comparingInt((Instrument instrument) ->
                        facilityById.get(instrument.getFacility().getFacilityId()).getDispOrd())
                .thenComparingInt(Instrument::getDispOrd)
                .thenComparing(Instrument::getInstrumentNm);
    }

    /** 계측기 1건 + 조회된 시계열 포인트를 시리즈 DTO 로 조립 — 데이터 0 설비는 빈 points 시리즈. */
    private InstrumentPowerSeries toSeries(
            Instrument instrument,
            Map<String, Facility> facilityById,
            Map<String, List<PowerTrendPoint>> pointsByInstrument) {
        Facility facility = facilityById.get(instrument.getFacility().getFacilityId());
        List<PowerTrendPoint> points =
                pointsByInstrument.getOrDefault(instrument.getInstrumentId(), List.of());
        return InstrumentPowerSeries.of(
                instrument.getInstrumentId(),
                instrument.getInstrumentNm(),
                instrument.getEquipType(),
                facility.getFacilityId(),
                facility.getFacilityNm(),
                points);
    }
}
