package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityDailyTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityDailyTimeSeriesDto.DailyTimeSeriesPoint;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredcRangeDto;
import com.mo.swtp.opt.repository.TagPredcRangeRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
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
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운전현황분석 10번 섹션 — 시설 단위 금일 하루치 계측+예측 시계열 조회 서비스.
 *
 * <p>활성 시설({@code use_yn = Y}, 지원 종류 PWTF/DWT/PRSF) 의 금일 하루치({@code 00:00 ~ 익일 00:00 전}) 시계열
 * 을 1분 단위로 응답한다. 계측(actual) 은 자정부터 현재시간까지, 예측(predc) 은 자정부터 익일 자정전까지
 * 범위를 가지며, 두 범위를 합본하여 단일 시계열에 묶는다 — 사용자 결정 (10번 섹션 ANALYZE1).</p>
 *
 * <p>5번 섹션 {@link FacilityOperatingStatusTimeSeriesService} 의 actual 헬퍼 9종 + 9번 섹션
 * {@link FacilityPredcOperatingStatusService} 의 predc 헬퍼 6종을 본 서비스에 동일 정책으로 재구현한다
 * (요청되지 않은 공통화 추상화 금지 — {@code coding-discipline.md §2}). 추출 후보는 사용 사례 4건 누적
 * 시점이나 별도 ANALYZE 사이클로 이연 (PLAN1 §4-1).</p>
 *
 * <p>6-SELECT 패턴 — Facility 1 + Instrument 1 + Tag 1 + RawData 1 (actual 범위) + TagPredcRange 1
 * (predc 범위) + 추가 진단 0 = 5회 (오버헤드 1회 미만 = 5번 6-SELECT 와 동등, PLAN1 §성능 검증).</p>
 *
 * <p>도메인 룰 인용:
 * {@code ot-integration.md §3} (OPS BAD 즉시 격상 — UNCERTAIN OPS 보수적 제외) ·
 * {@code entity-patterns.md §JPA JOINED 다형성 §도메인 룰} ({@code equip_type_cd} 필터 강제).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityDailyTimeSeriesService {

    /** 본 카드 지원 시설 종류 — RSV(저수조 펌프 미보유) · POINT(관로 분기점) 거부 (5·9번 동일). */
    private static final Set<FacilityType> SUPPORTED_TYPES = EnumSet.of(
            FacilityType.PWTF, FacilityType.DWT, FacilityType.PRSF);

    /** 측정 대상 계측기 종류 — 펌프(OPS·PWI) + 유량계(FRI). */
    private static final List<EquipType> TARGET_EQUIP_TYPES = List.of(EquipType.PUMP, EquipType.FLWMTR);

    /** 측정 대상 태그 유형 — OPS 가동상태 / PWI 순시전력 / FRI 유출유량. */
    private static final Set<TagMeasurementType> TARGET_TAG_TYPES = EnumSet.of(
            TagMeasurementType.OPS, TagMeasurementType.PWI, TagMeasurementType.FRI);

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;
    private final TagPredcRangeRepository tagPredcRangeRepository;

    /**
     * 활성 시설의 금일 하루치 계측+예측 시계열을 통합 응답한다.
     *
     * @param facilityId 활성 시설 ID
     * @return 시설 단위 금일 시계열 (actual·predc 합본)
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재 또는 비활성 시설
     * @throws RestApiException UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류
     */
    public FacilityDailyTimeSeriesDto findFacilityDailyTimeSeries(String facilityId) {
        Facility facility = findActiveFacilityOrThrow(facilityId);

        List<Instrument> instruments = instrumentRepository
                .findByFacilityIdAndEquipType(facilityId, TARGET_EQUIP_TYPES);
        if (instruments.isEmpty()) {
            return FacilityDailyTimeSeriesDto.of(
                    facility.getFacilityId(), facility.getFacilityNm(), List.of());
        }

        Map<String, List<Tag>> tagsByInstrument = loadTagsByInstrument(instruments);
        TimeRange actualRange = resolveActualRange();
        TimeRange predcRange = resolvePredcRange();

        List<String> tagSrlNos = tagsByInstrument.values().stream()
                .flatMap(List::stream).map(Tag::getTagSrlNo).toList();
        List<RawDataLatestDto> actuals = tagSrlNos.isEmpty() ? List.of()
                : rawDataRepository.findByTagSrlNosAndDtmRange(tagSrlNos, actualRange.start(), actualRange.end());
        List<TagPredcRangeDto> predcs = tagSrlNos.isEmpty() ? List.of()
                : tagPredcRangeRepository.findByTagSrlNosAndPredcDtmRange(tagSrlNos, predcRange.start(), predcRange.end());

        List<DailyTimeSeriesPoint> points = buildSeries(actuals, predcs, tagsByInstrument, instruments);
        return FacilityDailyTimeSeriesDto.of(
                facility.getFacilityId(), facility.getFacilityNm(), points);
    }

    /** 시점 범위 (start inclusive, end exclusive) — Service 내부 record. */
    private record TimeRange(LocalDateTime start, LocalDateTime end) {
    }

    /**
     * 단일 1분 시점의 actual·predc 7 항목 보관 record — buildSeries 3단 분해의 중간 산출물.
     *
     * @param actualElpwrAmt  계측 On 펌프 PWI 합산
     * @param actualFlwrt     계측 FRI
     * @param actualUnitQty   계측 전력원단위
     * @param predcElpwrAmt   예측 On 펌프 PWI 합산
     * @param predcFlwrt      예측 FRI
     * @param predcUnitQty    예측 전력원단위
     * @param predcPumpOnCnt  예측 가동 펌프 대수
     */
    private record PointParts(
            BigDecimal actualElpwrAmt,
            BigDecimal actualFlwrt,
            BigDecimal actualUnitQty,
            BigDecimal predcElpwrAmt,
            BigDecimal predcFlwrt,
            BigDecimal predcUnitQty,
            Integer predcPumpOnCnt) {
    }

    /** actual 시간 범위 — {@code today 00:00 ~ now()}. */
    private TimeRange resolveActualRange() {
        return new TimeRange(LocalDate.now().atStartOfDay(), LocalDateTime.now());
    }

    /** predc 시간 범위 — {@code today 00:00 ~ 익일 00:00 (exclusive)}. */
    private TimeRange resolvePredcRange() {
        LocalDate today = LocalDate.now();
        return new TimeRange(today.atStartOfDay(), today.plusDays(1).atStartOfDay());
    }

    /**
     * actual·predc 합본 시계열을 1분 단위 {@link DailyTimeSeriesPoint} 목록으로 변환.
     *
     * <p>3단 분해: actual slot map 생성 → predc 슬롯 overlay → TreeMap 자연정렬 변환.
     * TreeMap 사용으로 별도 Stream.sorted 호출 없이 dtm 오름차순 정렬 보장.</p>
     */
    private List<DailyTimeSeriesPoint> buildSeries(
            List<RawDataLatestDto> actuals,
            List<TagPredcRangeDto> predcs,
            Map<String, List<Tag>> tagsByInstrument,
            List<Instrument> instruments) {
        TreeMap<LocalDateTime, PointParts> combined = buildActualSlotMap(actuals, tagsByInstrument, instruments);
        overlayPredcSlots(combined, predcs, tagsByInstrument, instruments);
        return toSortedPoints(combined);
    }

    /**
     * actual 행들을 시점별 grouping 하여 PointParts(actual 3 항목만 채움) TreeMap 생성.
     *
     * <p>actual 부재(자정~현재시간 외) 슬롯은 본 단계에서 누락된다 — overlayPredcSlots 가 predc 슬롯을
     * 신규 추가하므로 actual·predc 둘 다 부재 시점만 자연 생략된다.</p>
     */
    private TreeMap<LocalDateTime, PointParts> buildActualSlotMap(
            List<RawDataLatestDto> actuals,
            Map<String, List<Tag>> tagsByInstrument,
            List<Instrument> instruments) {
        TreeMap<LocalDateTime, PointParts> map = new TreeMap<>();
        if (actuals.isEmpty()) {
            return map;
        }
        Map<LocalDateTime, List<RawDataLatestDto>> rowsByDtm = actuals.stream()
                .collect(Collectors.groupingBy(RawDataLatestDto::acqDtm));
        for (Map.Entry<LocalDateTime, List<RawDataLatestDto>> e : rowsByDtm.entrySet()) {
            Map<String, RawDataLatestDto> latestByTag = e.getValue().stream()
                    .collect(Collectors.toMap(RawDataLatestDto::tagSrlNo, r -> r));
            List<Pump> pumps = filterPumps(instruments);
            List<Pump> onPumps = pumps.stream()
                    .filter(p -> isPumpRunningActual(
                            pickLatestActual(tagsByInstrument, p.getInstrumentId(),
                                    TagMeasurementType.OPS, latestByTag)))
                    .toList();
            BigDecimal pwr = sumOnPumpPwrActual(onPumps, tagsByInstrument, latestByTag);
            RawDataLatestDto fri = selectFacilityFriActual(instruments, tagsByInstrument, latestByTag);
            BigDecimal flwrt = fri == null ? null : effectiveVal(fri);
            BigDecimal unit = computeUnitConsumptionActual(pwr, fri);
            map.put(e.getKey(), new PointParts(pwr, flwrt, unit, null, null, null, null));
        }
        return map;
    }

    /**
     * predc 행들을 시점별 grouping 하여 기존 PointParts 의 predc 4 항목을 채우거나 신규 슬롯을 추가.
     *
     * <p>combined map 에 동일 dtm 키가 있으면 actual 3 항목 보존 + predc 4 항목 갱신, 없으면 actual 3 항목
     * NULL + predc 4 항목 채운 신규 PointParts 추가.</p>
     */
    private void overlayPredcSlots(
            TreeMap<LocalDateTime, PointParts> combined,
            List<TagPredcRangeDto> predcs,
            Map<String, List<Tag>> tagsByInstrument,
            List<Instrument> instruments) {
        if (predcs.isEmpty()) {
            return;
        }
        Map<LocalDateTime, List<TagPredcRangeDto>> rowsByDtm = predcs.stream()
                .collect(Collectors.groupingBy(TagPredcRangeDto::predcDtm));
        for (Map.Entry<LocalDateTime, List<TagPredcRangeDto>> e : rowsByDtm.entrySet()) {
            Map<String, TagPredcRangeDto> latestByTag = e.getValue().stream()
                    .collect(Collectors.toMap(TagPredcRangeDto::tagSrlNo, r -> r));
            List<Pump> pumps = filterPumps(instruments);
            List<Pump> onPumps = pumps.stream()
                    .filter(p -> isPumpRunningPredc(
                            pickLatestPredc(tagsByInstrument, p.getInstrumentId(),
                                    TagMeasurementType.OPS, latestByTag)))
                    .toList();
            BigDecimal pwr = sumOnPumpPwrPredc(onPumps, tagsByInstrument, latestByTag);
            TagPredcRangeDto fri = selectFacilityFriPredc(instruments, tagsByInstrument, latestByTag);
            BigDecimal flwrt = fri == null ? null : fri.predcVal();
            BigDecimal unit = computeUnitConsumptionPredc(pwr, fri);
            PointParts prev = combined.get(e.getKey());
            PointParts next = (prev == null)
                    ? new PointParts(null, null, null, pwr, flwrt, unit, onPumps.size())
                    : new PointParts(prev.actualElpwrAmt(), prev.actualFlwrt(), prev.actualUnitQty(),
                            pwr, flwrt, unit, onPumps.size());
            combined.put(e.getKey(), next);
        }
    }

    /** TreeMap 자연정렬 결과를 DailyTimeSeriesPoint List 로 변환 — 별도 Stream.sorted 미사용. */
    private List<DailyTimeSeriesPoint> toSortedPoints(TreeMap<LocalDateTime, PointParts> combined) {
        List<DailyTimeSeriesPoint> result = new ArrayList<>(combined.size());
        for (Map.Entry<LocalDateTime, PointParts> e : combined.entrySet()) {
            PointParts p = e.getValue();
            result.add(DailyTimeSeriesPoint.of(
                    e.getKey(),
                    p.actualElpwrAmt(), p.actualFlwrt(), p.actualUnitQty(),
                    p.predcElpwrAmt(), p.predcFlwrt(), p.predcUnitQty(),
                    p.predcPumpOnCnt()));
        }
        return result;
    }

    /**
     * 활성 시설 검증 + 본 카드 지원 시설 종류(PWTF·DWT·PRSF) 필터 — 5·9번 동일 정책 재구현.
     *
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재·비활성 시설
     * @throws RestApiException UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT
     */
    private Facility findActiveFacilityOrThrow(String facilityId) {
        Facility facility = facilityRepository.findById(facilityId)
                .orElseThrow(() -> new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND));
        if (facility.getUseYn() != YnType.Y) {
            throw new RestApiException(FacilityErrorCode.FACILITY_NOT_FOUND);
        }
        if (!SUPPORTED_TYPES.contains(facility.getFacilityType())) {
            throw new RestApiException(FacilityErrorCode.UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS);
        }
        return facility;
    }

    /** 시설 직속 계측기들의 활성 태그를 OPS/PWI/FRI 필터 후 인스트루먼트별로 그룹화 — 5·9번 동일. */
    private Map<String, List<Tag>> loadTagsByInstrument(List<Instrument> instruments) {
        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(t -> TARGET_TAG_TYPES.contains(t.getTagSeCd()))
                .collect(Collectors.groupingBy(t -> t.getInstrument().getInstrumentId()));
    }

    /** equip_type_cd = 'PUMP' 필터 강제 — 다형성 부모 List<Instrument> 에서 Pump 자식만 추출. */
    private List<Pump> filterPumps(List<Instrument> instruments) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.PUMP)
                .map(i -> (Pump) i)
                .toList();
    }

    /** 인스트루먼트의 지정 측정 유형 actual 1건 추출 (없으면 null) — 5번 동일. */
    private RawDataLatestDto pickLatestActual(
            Map<String, List<Tag>> tagsByInstrument,
            String instrumentId,
            TagMeasurementType targetType,
            Map<String, RawDataLatestDto> latestByTag) {
        return tagsByInstrument.getOrDefault(instrumentId, List.of()).stream()
                .filter(t -> t.getTagSeCd() == targetType)
                .map(t -> latestByTag.get(t.getTagSrlNo()))
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
    }

    /** 인스트루먼트의 지정 측정 유형 predc 1건 추출 (없으면 null) — 9번 동일. */
    private TagPredcRangeDto pickLatestPredc(
            Map<String, List<Tag>> tagsByInstrument,
            String instrumentId,
            TagMeasurementType targetType,
            Map<String, TagPredcRangeDto> latestByTag) {
        return tagsByInstrument.getOrDefault(instrumentId, List.of()).stream()
                .filter(t -> t.getTagSeCd() == targetType)
                .map(t -> latestByTag.get(t.getTagSrlNo()))
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
    }

    /** actual 값 선택 정책 — corrVal 우선, NULL 시 rawVal (5번 동일). */
    private BigDecimal effectiveVal(RawDataLatestDto r) {
        if (r == null) {
            return null;
        }
        return r.corrVal() != null ? r.corrVal() : r.rawVal();
    }

    /**
     * actual 펌프 On 판정 — null OR qualityCd != GOOD OR effectiveVal != 1.0 → false (5번 동일).
     *
     * <p>UNCERTAIN OPS 도 On 제외 — {@code ot-integration.md §3} OPS BAD 즉시 격상 정책의 보수적 확장.</p>
     */
    private boolean isPumpRunningActual(RawDataLatestDto ops) {
        if (ops == null || ops.qualityCd() != QualityCode.GOOD) {
            return false;
        }
        BigDecimal val = effectiveVal(ops);
        return val != null && val.compareTo(BigDecimal.ONE) == 0;
    }

    /**
     * predc 펌프 On 판정 — null OR predcVal != 1.0 → false (9번 동일).
     *
     * <p>{@code predc_1m_h} 는 {@code quality_cd} 컬럼 부재 — SCADA QUALITY 분기 미적용.</p>
     */
    private boolean isPumpRunningPredc(TagPredcRangeDto ops) {
        if (ops == null) {
            return false;
        }
        BigDecimal val = ops.predcVal();
        return val != null && val.compareTo(BigDecimal.ONE) == 0;
    }

    /**
     * actual On 펌프들의 PWI 합산 — GOOD 만 합산, null/BAD/UNCERTAIN 전액 제외 (5번 동일).
     * On 펌프 0대 또는 모든 PWI 부재 시 BigDecimal.ZERO 반환.
     */
    private BigDecimal sumOnPumpPwrActual(
            List<Pump> onPumps,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        return onPumps.stream()
                .map(p -> pickLatestActual(tagsByInstrument, p.getInstrumentId(),
                        TagMeasurementType.PWI, latestByTag))
                .filter(pwi -> pwi != null && pwi.qualityCd() == QualityCode.GOOD)
                .map(this::effectiveVal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * predc On 펌프들의 PWI 예측 합산 — null 만 제외 (9번 동일).
     * 예측 On 펌프 0대 또는 모든 PWI 결측 시 BigDecimal.ZERO 반환.
     */
    private BigDecimal sumOnPumpPwrPredc(
            List<Pump> onPumps,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, TagPredcRangeDto> latestByTag) {
        return onPumps.stream()
                .map(p -> pickLatestPredc(tagsByInstrument, p.getInstrumentId(),
                        TagMeasurementType.PWI, latestByTag))
                .filter(pwi -> pwi != null)
                .map(TagPredcRangeDto::predcVal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 시설 직속 FLWMTR 의 첫 GOOD FRI actual 측정값 — 5번 동일 (parent_facility_id 재귀 미적용). */
    private RawDataLatestDto selectFacilityFriActual(
            List<Instrument> instruments,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.FLWMTR)
                .map(i -> pickLatestActual(tagsByInstrument, i.getInstrumentId(),
                        TagMeasurementType.FRI, latestByTag))
                .filter(r -> r != null && r.qualityCd() == QualityCode.GOOD)
                .findFirst()
                .orElse(null);
    }

    /** 시설 직속 FLWMTR 의 첫 FRI predc 측정값 — 9번 동일 (quality_cd 분기 부재). */
    private TagPredcRangeDto selectFacilityFriPredc(
            List<Instrument> instruments,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, TagPredcRangeDto> latestByTag) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.FLWMTR)
                .map(i -> pickLatestPredc(tagsByInstrument, i.getInstrumentId(),
                        TagMeasurementType.FRI, latestByTag))
                .filter(r -> r != null)
                .findFirst()
                .orElse(null);
    }

    /**
     * actual 전력원단위 (kWh/m³) — 분자/분모 0/NULL/BAD/부재 4 케이스 모두 null (5번 동일).
     * 정상 시 totalElpwrAmt / friVal (scale=4, RoundingMode.HALF_UP).
     */
    private BigDecimal computeUnitConsumptionActual(BigDecimal totalElpwrAmt, RawDataLatestDto fri) {
        if (totalElpwrAmt == null || totalElpwrAmt.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (fri == null || fri.qualityCd() != QualityCode.GOOD) {
            return null;
        }
        BigDecimal friVal = effectiveVal(fri);
        if (friVal == null || friVal.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return totalElpwrAmt.divide(friVal, 4, RoundingMode.HALF_UP);
    }

    /**
     * predc 전력원단위 (kWh/m³) — 3 케이스 (분자 0/null · FRI 부재 · 분모 null/0) 모두 null (9번 동일).
     * 4번 대비 quality_cd 분기 2건 제거.
     */
    private BigDecimal computeUnitConsumptionPredc(BigDecimal totalElpwrAmt, TagPredcRangeDto fri) {
        if (totalElpwrAmt == null || totalElpwrAmt.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        if (fri == null) {
            return null;
        }
        BigDecimal friVal = fri.predcVal();
        if (friVal == null || friVal.compareTo(BigDecimal.ZERO) == 0) {
            return null;
        }
        return totalElpwrAmt.divide(friVal, 4, RoundingMode.HALF_UP);
    }
}
