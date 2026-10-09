package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityDownstreamDataType;
import com.mo.swtp.facility.dto.DownstreamPoint;
import com.mo.swtp.facility.dto.FacilityDownstreamLevelDto;
import com.mo.swtp.facility.dto.FacilityDownstreamLevelDto.LevelSeries;
import com.mo.swtp.facility.dto.FacilityDownstreamMeasureDto;
import com.mo.swtp.facility.dto.FacilityDownstreamMeasureDto.MeasureSeries;
import com.mo.swtp.facility.dto.FacilityDownstreamTimeSeriesDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.facility.service.FacilityDownstreamTreeResolver.DownstreamTopology;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredcOutflowDto;
import com.mo.swtp.opt.repository.TagPredcOutflowRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataOutflowDto;
import com.mo.swtp.raw.repository.RawDataOutflowRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.IoCode;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운전현황분석 8번 섹션 — 활성 루트 시설의 재귀 하위(정수지·분기점·배수지) 계측+예측 시계열 조회 서비스.
 *
 * <p>12번 섹션 활성 시설(루트) 을 상위로 바라보고, {@link FacilityDownstreamTreeResolver} 가 도출한 재귀 하위
 * 표출 대상(DWT 자식을 가진 PWTF·POINT) 에 대해 금일({@code 00:00 ~ 현재시간}) 시계열을 1분 단위로 응답한다.
 * 단일 엔드포인트가 {@code dataType} (수요량/관압/수위 탭) 로 분기한다:</p>
 * <ul>
 *   <li>{@code DEMAND}/{@code PRESSURE} — 표출대상 시설당 1 시리즈. 그 시설의 유출(송수) FLWMTR
 *       ({@code io_cd ∈ {OUTPUT, BIDIR}} 첫 매치) 의 FRI(수요량)·PRI(관압). 유량·압력 모두 유량계 유출값 기준
 *       (사용자 Q5 확정 2026-06-01) — 별도 압력계(PRSMTR) 미사용.</li>
 *   <li>{@code LEVEL} — 표출대상의 자식 DWT 의 수위계(LVMTR) 당 1 시리즈. LEI(수위), io_cd 무필터.</li>
 * </ul>
 *
 * <p>각 슬롯은 계측값(GOOD, {@code corr_val} 우선)·예측값({@code predc_val})·대비율({@code predcVal/actualVal×100},
 * 소수 1자리) 을 담는다. 계측·예측 양쪽 부재 슬롯은 생략한다. 루트 시설 종류 제한 없음 — 미존재·비활성만
 * 404 ({@code FACILITY_NOT_FOUND}). 4·7번 헬퍼를 재사용하지 않고 본 서비스에 동일 정책으로 재구현한다
 * (요청되지 않은 공통화 추상화 금지 — {@code coding-discipline.md §2·§3} · 사용자 메모리 "사이클 간 자산 자동
 * 원용 금지"). io_cd 유출 필터는 4번 섹션 {@code DwtStateService} 동일 정책.</p>
 *
 * <p>N+1 회피: 표출대상 FLWMTR(또는 DWT LVMTR) 을 IN 절 단일 SQL 로 가져오고, 모든 시리즈의 태그를 모아
 * 계측·예측 범위 쿼리를 각 1회만 발행한다 ({@code db/partitioning-and-retention.md} 월 RANGE 파티션 프루닝).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityDownstreamTimeSeriesService {

    /** 수요량·관압 표출대상의 유출 유량계 조회 대상 — FLWMTR 단일. */
    private static final List<EquipType> FLWMTR_ONLY = List.of(EquipType.FLWMTR);

    /** 수위 자식 DWT 의 수위계 조회 대상 — LVMTR 단일. */
    private static final List<EquipType> LVMTR_ONLY = List.of(EquipType.LVMTR);

    /** 유출 후보 io_cd 코드 — OUTPUT 또는 BIDIR (4번 섹션 {@code DwtStateService.OUTLET_IO_CODES} 동일). */
    private static final Set<IoCode> OUTLET_IO_CODES = EnumSet.of(IoCode.OUTPUT, IoCode.BIDIR);

    /** 대비율 백분율 계수. */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private final FacilityRepository facilityRepository;
    private final FacilityDownstreamTreeResolver downstreamTreeResolver;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataOutflowRepository rawDataOutflowRepository;
    private final TagPredcOutflowRepository tagPredcOutflowRepository;

    /**
     * 활성 루트 시설의 재귀 하위 계측+예측 시계열을 데이터 종류별로 응답한다.
     *
     * @param facilityId 활성 루트 시설 ID (12번 섹션 활성 시설)
     * @param dataType   표출 데이터 종류 (DEMAND/PRESSURE/LEVEL)
     * @return 다형성 응답 DTO (Measure 또는 Level)
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재 또는 비활성 루트 시설
     */
    public FacilityDownstreamTimeSeriesDto findDownstreamTimeSeries(
            String facilityId, FacilityDownstreamDataType dataType) {
        Facility root = findActiveFacilityOrThrow(facilityId);
        DownstreamTopology topology = downstreamTreeResolver.resolve(root);
        return switch (dataType) {
            case DEMAND -> buildMeasure(root, topology, dataType, TagMeasurementType.FRI);
            case PRESSURE -> buildMeasure(root, topology, dataType, TagMeasurementType.PRI);
            case LEVEL -> buildLevel(root, topology);
        };
    }

    /** 표출대상 시설별 유출 FLWMTR + 측정 태그 보관 — buildMeasure 2-pass 의 중간 산출물. */
    private record MeasurePlan(
            Facility target,
            Instrument outletFlwmtr,
            boolean multipleOutletFlwmtr,
            String tagSrlNo) {
    }

    /** 수위계별 배수지·수위계·상위 표출대상·LEI 태그 보관 — buildLevel 2-pass 의 중간 산출물. */
    private record LevelPlan(
            Facility dwt,
            Instrument lvmtr,
            Facility parentTarget,
            String tagSrlNo) {
    }

    /** 시리즈 태그 묶음의 계측·예측 범위 조회 결과 — 태그 시리얼번호 키로 그룹화. */
    private record TimeSeriesData(
            Map<String, List<RawDataOutflowDto>> actualsByTag,
            Map<String, List<TagPredcOutflowDto>> predcsByTag) {
    }

    /** 슬롯의 계측·예측 값 짝 보관 — 병합 TreeMap 의 value. */
    private record ValPair(BigDecimal actual, BigDecimal predc) {
    }

    /**
     * 활성 루트 시설 검증 — 미존재·비활성 시 404. 루트 시설 종류 제한 없음 (PLAN1 §가정 A-ROOT·A6).
     *
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재·비활성
     */
    private Facility findActiveFacilityOrThrow(String facilityId) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
        if (facility.getUseYn() != YnType.Y) {
            throw new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND);
        }
        return facility;
    }

    // ────────────────────────────────────────────────────────────────────────
    // DEMAND / PRESSURE — 표출대상 시설당 유출 FLWMTR 의 FRI/PRI 시리즈

    /**
     * 수요량(FRI)/관압(PRI) — 표출대상 시설마다 유출 FLWMTR 의 측정 시리즈를 구성한다.
     *
     * @param root         활성 루트 시설
     * @param topology     재귀 하위 도출 결과
     * @param dataType     표출 데이터 종류 (DEMAND/PRESSURE)
     * @param measureType  측정 태그 유형 (FRI/PRI)
     */
    private FacilityDownstreamMeasureDto buildMeasure(
            Facility root,
            DownstreamTopology topology,
            FacilityDownstreamDataType dataType,
            TagMeasurementType measureType) {
        List<Facility> targets = topology.displayTargets();
        if (targets.isEmpty()) {
            return FacilityDownstreamMeasureDto.of(
                    root.getFacilityId(), root.getFacilityNm(), dataType, List.of());
        }
        List<String> targetIds = targets.stream().map(Facility::getFacilityId).toList();
        List<Instrument> flwmtrs = instrumentRepository.findByFacilityIdInAndEquipType(targetIds, FLWMTR_ONLY);
        Map<String, List<Instrument>> flwmtrsByFacility = groupByFacility(flwmtrs);
        Map<String, List<Tag>> tagsByInstrument = loadTagsByInstrument(flwmtrs);

        List<MeasurePlan> plans = targets.stream()
                .map(t -> planMeasureSeries(t, flwmtrsByFacility, tagsByInstrument, measureType))
                .toList();
        TimeSeriesData data = loadTimeSeries(collectTagSrlNos(plans.stream().map(MeasurePlan::tagSrlNo)));

        List<MeasureSeries> series = plans.stream().map(p -> toMeasureSeries(p, data)).toList();
        return FacilityDownstreamMeasureDto.of(
                root.getFacilityId(), root.getFacilityNm(), dataType, series);
    }

    /** 표출대상 1건의 유출 FLWMTR(첫 매치) + 측정 태그를 도출한다 — 유출 FLWMTR 다중 시 플래그 표기. */
    private MeasurePlan planMeasureSeries(
            Facility target,
            Map<String, List<Instrument>> flwmtrsByFacility,
            Map<String, List<Tag>> tagsByInstrument,
            TagMeasurementType measureType) {
        List<Instrument> targetFlwmtrs = flwmtrsByFacility.getOrDefault(target.getFacilityId(), List.of());
        List<Instrument> outlets = filterOutletFlwmtrs(targetFlwmtrs, tagsByInstrument);
        Instrument outlet = outlets.isEmpty() ? null : outlets.get(0);
        String tagSrlNo = (outlet == null)
                ? null
                : pickTagSrlNo(tagsByInstrument, outlet.getInstrumentId(), measureType);
        return new MeasurePlan(target, outlet, outlets.size() > 1, tagSrlNo);
    }

    /** MeasurePlan + 시계열 데이터를 응답 시리즈로 변환 — 유출 FLWMTR 부재 시 instrument 필드 null + 빈 points. */
    private MeasureSeries toMeasureSeries(MeasurePlan plan, TimeSeriesData data) {
        Instrument outlet = plan.outletFlwmtr();
        return MeasureSeries.of(
                plan.target().getFacilityId(),
                plan.target().getFacilityNm(),
                plan.target().getFacilityType(),
                outlet == null ? null : outlet.getInstrumentId(),
                outlet == null ? null : outlet.getInstrumentNm(),
                plan.multipleOutletFlwmtr(),
                buildPoints(plan.tagSrlNo(), data));
    }

    /** FLWMTR 중 산하 태그의 {@code io_cd} 가 OUTPUT·BIDIR 와 1건이라도 일치하는 유출 계측기만 추출 (4번 섹션 동형). */
    private List<Instrument> filterOutletFlwmtrs(
            List<Instrument> flwmtrs,
            Map<String, List<Tag>> tagsByInstrument) {
        List<Instrument> matched = new ArrayList<>();
        for (Instrument flwmtr : flwmtrs) {
            List<Tag> tags = tagsByInstrument.getOrDefault(flwmtr.getInstrumentId(), List.of());
            boolean outlet = tags.stream().anyMatch(t -> OUTLET_IO_CODES.contains(t.getIoCd()));
            if (outlet) {
                matched.add(flwmtr);
            }
        }
        return matched;
    }

    // ────────────────────────────────────────────────────────────────────────
    // LEVEL — 자식 DWT 의 수위계당 LEI 시리즈

    /**
     * 수위(LEI) — 표출대상의 자식 DWT 들의 수위계마다 시리즈를 구성한다 (수위계당 1, parent 표출대상 그룹핑).
     *
     * @param root     활성 루트 시설
     * @param topology 재귀 하위 도출 결과
     */
    private FacilityDownstreamLevelDto buildLevel(Facility root, DownstreamTopology topology) {
        List<Facility> allDwts = topology.dwtsByTargetId().values().stream()
                .flatMap(List::stream).toList();
        if (allDwts.isEmpty()) {
            return FacilityDownstreamLevelDto.of(root.getFacilityId(), root.getFacilityNm(), List.of());
        }
        List<String> dwtIds = allDwts.stream().map(Facility::getFacilityId).toList();
        List<Instrument> lvmtrs = instrumentRepository.findByFacilityIdInAndEquipType(dwtIds, LVMTR_ONLY);
        Map<String, List<Instrument>> lvmtrsByDwt = groupByFacility(lvmtrs);
        Map<String, List<Tag>> tagsByInstrument = loadTagsByInstrument(lvmtrs);

        List<LevelPlan> plans = planLevelSeries(topology, lvmtrsByDwt, tagsByInstrument);
        TimeSeriesData data = loadTimeSeries(collectTagSrlNos(plans.stream().map(LevelPlan::tagSrlNo)));

        List<LevelSeries> series = plans.stream().map(p -> toLevelSeries(p, data)).toList();
        return FacilityDownstreamLevelDto.of(root.getFacilityId(), root.getFacilityNm(), series);
    }

    /** 표출대상별 자식 DWT 의 수위계마다 LevelPlan 1건 생성 — parent 는 DWT 의 상위 표출대상. */
    private List<LevelPlan> planLevelSeries(
            DownstreamTopology topology,
            Map<String, List<Instrument>> lvmtrsByDwt,
            Map<String, List<Tag>> tagsByInstrument) {
        Map<String, Facility> targetById = topology.displayTargets().stream()
                .collect(Collectors.toMap(Facility::getFacilityId, t -> t));
        List<LevelPlan> plans = new ArrayList<>();
        for (Map.Entry<String, List<Facility>> entry : topology.dwtsByTargetId().entrySet()) {
            Facility parentTarget = targetById.get(entry.getKey());
            for (Facility dwt : entry.getValue()) {
                for (Instrument lvmtr : lvmtrsByDwt.getOrDefault(dwt.getFacilityId(), List.of())) {
                    String tagSrlNo = pickTagSrlNo(
                            tagsByInstrument, lvmtr.getInstrumentId(), TagMeasurementType.LEI);
                    plans.add(new LevelPlan(dwt, lvmtr, parentTarget, tagSrlNo));
                }
            }
        }
        return plans;
    }

    /** LevelPlan + 시계열 데이터를 응답 시리즈로 변환. */
    private LevelSeries toLevelSeries(LevelPlan plan, TimeSeriesData data) {
        return LevelSeries.of(
                plan.dwt().getFacilityId(),
                plan.dwt().getFacilityNm(),
                plan.dwt().getFacilityType(),
                plan.lvmtr().getInstrumentId(),
                plan.lvmtr().getInstrumentNm(),
                plan.parentTarget().getFacilityId(),
                plan.parentTarget().getFacilityNm(),
                buildPoints(plan.tagSrlNo(), data));
    }

    // ────────────────────────────────────────────────────────────────────────
    // 공통 — 계측기/태그 로딩, 시계열 병합, 대비율

    /** 계측기 목록을 소속 시설 ID 로 그룹화 — IN 절 조회 결과를 시설 단위로 재구성. */
    private Map<String, List<Instrument>> groupByFacility(List<Instrument> instruments) {
        return instruments.stream()
                .collect(Collectors.groupingBy(i -> i.getFacility().getFacilityId()));
    }

    /** 계측기 산하 활성 태그를 인스트루먼트별로 그룹화 (측정 유형 무필터 — pickTagSrlNo 가 유형 선별). */
    private Map<String, List<Tag>> loadTagsByInstrument(List<Instrument> instruments) {
        if (instruments.isEmpty()) {
            return Map.of();
        }
        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .collect(Collectors.groupingBy(t -> t.getInstrument().getInstrumentId()));
    }

    /** 인스트루먼트의 지정 측정 유형 첫 태그 시리얼번호 (없으면 null). */
    private String pickTagSrlNo(
            Map<String, List<Tag>> tagsByInstrument,
            String instrumentId,
            TagMeasurementType targetType) {
        return tagsByInstrument.getOrDefault(instrumentId, List.of()).stream()
                .filter(t -> t.getTagSeCd() == targetType)
                .map(Tag::getTagSrlNo)
                .findFirst()
                .orElse(null);
    }

    /** 시리즈 태그 시리얼번호 스트림을 null 제거 + 중복 제거하여 범위 쿼리 입력 목록으로 수집. */
    private List<String> collectTagSrlNos(java.util.stream.Stream<String> tagSrlNos) {
        return tagSrlNos.filter(Objects::nonNull).distinct().toList();
    }

    /** 태그 묶음의 금일(00:00~현재) 계측·예측 범위 조회 — 각 1회 SQL, 태그 시리얼번호 키 그룹화. */
    private TimeSeriesData loadTimeSeries(List<String> tagSrlNos) {
        if (tagSrlNos.isEmpty()) {
            return new TimeSeriesData(Map.of(), Map.of());
        }
        LocalDateTime startDtm = LocalDate.now().atStartOfDay();
        LocalDateTime endDtm = LocalDateTime.now();
        Map<String, List<RawDataOutflowDto>> actualsByTag = rawDataOutflowRepository
                .findByTagSrlNosAndDtmRange(tagSrlNos, startDtm, endDtm).stream()
                .collect(Collectors.groupingBy(RawDataOutflowDto::tagSrlNo));
        Map<String, List<TagPredcOutflowDto>> predcsByTag = tagPredcOutflowRepository
                .findByTagSrlNosAndPredcDtmRange(tagSrlNos, startDtm, endDtm).stream()
                .collect(Collectors.groupingBy(TagPredcOutflowDto::tagSrlNo));
        return new TimeSeriesData(actualsByTag, predcsByTag);
    }

    /**
     * 단일 태그의 계측+예측을 1분 {@link DownstreamPoint} 목록으로 병합한다.
     *
     * <p>actual slot map 생성 → predc 슬롯 overlay → TreeMap 자연정렬 + 양쪽 부재 슬롯 생략 + 대비율 계산.
     * 태그가 없으면(유출 FLWMTR·LVMTR 또는 측정 태그 부재) 빈 목록.</p>
     */
    private List<DownstreamPoint> buildPoints(String tagSrlNo, TimeSeriesData data) {
        if (tagSrlNo == null) {
            return List.of();
        }
        TreeMap<LocalDateTime, ValPair> map = new TreeMap<>();
        fillActual(map, data.actualsByTag().getOrDefault(tagSrlNo, List.of()));
        overlayPredc(map, data.predcsByTag().getOrDefault(tagSrlNo, List.of()));
        return toPoints(map);
    }

    /** 계측 행을 시점 키로 채움 — GOOD 측정값만 (BAD/UNCERTAIN→null), corr_val 우선. */
    private void fillActual(TreeMap<LocalDateTime, ValPair> map, List<RawDataOutflowDto> actuals) {
        for (RawDataOutflowDto row : actuals) {
            map.put(row.acqDtm(), new ValPair(effectiveValGood(row), null));
        }
    }

    /** 예측 행을 시점 키로 overlay — 기존 계측 짝 보존하며 예측값 갱신 또는 신규 슬롯 추가. */
    private void overlayPredc(TreeMap<LocalDateTime, ValPair> map, List<TagPredcOutflowDto> predcs) {
        for (TagPredcOutflowDto row : predcs) {
            ValPair prev = map.get(row.predcDtm());
            BigDecimal actual = (prev == null) ? null : prev.actual();
            map.put(row.predcDtm(), new ValPair(actual, row.predcVal()));
        }
    }

    /** TreeMap 자연정렬 결과를 슬롯 목록으로 변환 — 계측·예측 양쪽 null 슬롯 생략 + 슬롯별 대비율 계산. */
    private List<DownstreamPoint> toPoints(TreeMap<LocalDateTime, ValPair> map) {
        List<DownstreamPoint> result = new ArrayList<>(map.size());
        for (Map.Entry<LocalDateTime, ValPair> e : map.entrySet()) {
            ValPair p = e.getValue();
            if (p.actual() == null && p.predc() == null) {
                continue;
            }
            result.add(DownstreamPoint.of(e.getKey(), p.actual(), p.predc(), computeRatio(p.actual(), p.predc())));
        }
        return result;
    }

    /** 계측 GOOD 값 — GOOD 만 채택 (BAD/UNCERTAIN·null → null), corr_val 우선 후 raw_val (4·7번 동형). */
    private BigDecimal effectiveValGood(RawDataOutflowDto r) {
        if (r == null || r.qualityCd() != QualityCode.GOOD) {
            return null;
        }
        return r.corrVal() != null ? r.corrVal() : r.rawVal();
    }

    /**
     * 대비율 — {@code predcVal / actualVal × 100}, 소수 1자리 HALF_UP.
     *
     * <p>계측값이 null·0 이거나 예측값이 null 이면 null (0 나누기·무의미 비율 방어 — PLAN1 §가정 A-RATIO).</p>
     */
    private BigDecimal computeRatio(BigDecimal actual, BigDecimal predc) {
        if (actual == null || actual.compareTo(BigDecimal.ZERO) == 0 || predc == null) {
            return null;
        }
        return predc.multiply(HUNDRED).divide(actual, 1, RoundingMode.HALF_UP);
    }
}
