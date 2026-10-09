package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityOperatingStatusCompareType;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityOperatingStatusTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityOperatingStatusTimeSeriesDto.TimeSeriesPoint;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
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
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운전현황분석 5번 섹션 — 시설 단위 1분 단위 운영 현황 시계열 조회 서비스 (옵션 T).
 *
 * <p>활성 시설({@code use_yn = Y}, 지원 종류 PWTF/DWT/PRSF) 의 금일(00:00~현재) 실측과 비교 기간
 * ({@code YESTERDAY 또는 LAST_WEEK}) 실측을 <b>시간:분(HH:mm) 키로 머지한 1440 고정 단일 시계열</b>
 * ({@code series}) 로 응답한다 — 4번 섹션 단건 운영 현황 카드를 1분 시계열로 확장 후, 금일·비교일이 서로
 * 다른 날짜이므로 날짜를 제거한 "HH:mm" 키로 머지하여 프론트엔드 머지 부담을 0 으로 만든다
 * (운전현황분석-5번섹션-DTO재설계 PLAN1).</p>
 *
 * <p>4번 섹션 {@code FacilityOperatingStatusService} 의 헬퍼 9종 (시설 검증·태그 그룹화·펌프 필터·
 * pickLatest·effectiveVal·isPumpRunning·sumOnPumpPwr·selectFacilityFri·computeUnitConsumption) 을
 * 본 서비스에 동일 정책으로 재구현한다 (요청되지 않은 공통화 추상화 금지 —
 * {@code coding-discipline.md §2}). 추출 후보는 사용 사례 2건 누적 시점에 별도 ANALYZE 로 결정.</p>
 *
 * <p>6-SELECT 패턴 — Facility 1 + Instrument 1 + Tag 1 + RawData 2 (today + comparison 분리) +
 * 추가 진단 0. 4번 섹션 4-SELECT 패턴 + 시계열 2회 호출 = 6회 (PLAN1 §성능 검증).</p>
 *
 * <p>도메인 룰 인용:
 * {@code ot-integration.md §3} (OPS BAD 즉시 격상 — UNCERTAIN OPS 보수적 제외) ·
 * {@code entity-patterns.md §JPA JOINED 다형성 §도메인 룰} ({@code equip_type_cd} 필터 강제).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityOperatingStatusTimeSeriesService {

    /** 하루 1분 슬롯 수 — 00:00 ~ 23:59 고정 1440 행. */
    private static final int MINUTES_PER_DAY = 1440;

    /** 본 카드 지원 시설 종류 — RSV(저수조 펌프 미보유) · POINT(관로 분기점) 거부 (PLAN1 가정 결정). */
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

    /**
     * 활성 시설의 운영 현황 시계열을 1440 고정 머지 단일 series 로 응답한다.
     *
     * @param facilityId  활성 시설 ID
     * @param compareType 비교 기간 선택값 (YESTERDAY 또는 LAST_WEEK)
     * @return 시설 단위 운영 현황 시계열 (1440 고정 머지 단일 series + baseDate/comparisonDate 메타)
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재 또는 비활성 시설
     * @throws RestApiException UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류
     */
    public FacilityOperatingStatusTimeSeriesDto findFacilityOperatingStatusTimeSeries(
            String facilityId, FacilityOperatingStatusCompareType compareType) {
        Facility facility = findActiveFacilityOrThrow(facilityId);
        LocalDate baseDate = LocalDate.now();
        LocalDate comparisonDate = baseDate.minusDays(daysAgoOf(compareType));

        List<Instrument> instruments = instrumentRepository
                .findByFacilityIdAndEquipType(facilityId, TARGET_EQUIP_TYPES);
        if (instruments.isEmpty()) {
            return FacilityOperatingStatusTimeSeriesDto.of(
                    facility.getFacilityId(), facility.getFacilityNm(),
                    baseDate, compareType, comparisonDate,
                    buildOptionTSeries(Map.of(), Map.of()));
        }

        Map<String, List<Tag>> tagsByInstrument = loadTagsByInstrument(instruments);
        Map<String, MinuteMetrics> todayByTime = collectMinuteMetrics(
                tagsByInstrument, instruments, resolveTodayRange());
        Map<String, MinuteMetrics> comparisonByTime = collectMinuteMetrics(
                tagsByInstrument, instruments, resolveComparisonRange(comparisonDate));

        List<TimeSeriesPoint> series = buildOptionTSeries(todayByTime, comparisonByTime);

        return FacilityOperatingStatusTimeSeriesDto.of(
                facility.getFacilityId(), facility.getFacilityNm(),
                baseDate, compareType, comparisonDate, series);
    }

    /** 시점 범위 (start inclusive, end exclusive) — Service 내부 record. */
    private record TimeRange(LocalDateTime start, LocalDateTime end) {
    }

    /** 1분 슬롯의 머지 재료 — On 펌프 대수 + 전력원단위 (today·comparison 공용). */
    private record MinuteMetrics(int onPumpCnt, BigDecimal elpwrUnitQty) {
    }

    /** 비교 기간 → 일수 오프셋. YESTERDAY = 1, LAST_WEEK = 7. */
    private int daysAgoOf(FacilityOperatingStatusCompareType compareType) {
        return switch (compareType) {
            case YESTERDAY -> 1;
            case LAST_WEEK -> 7;
        };
    }

    /** 금일 시간 범위 — {@code today 00:00 ~ now()}. */
    private TimeRange resolveTodayRange() {
        return new TimeRange(LocalDate.now().atStartOfDay(), LocalDateTime.now());
    }

    /** 비교 일자 시간 범위 — {@code comparisonDate 00:00 ~ 23:59:59.999999999}. */
    private TimeRange resolveComparisonRange(LocalDate comparisonDate) {
        return new TimeRange(comparisonDate.atStartOfDay(), comparisonDate.atTime(LocalTime.MAX));
    }

    /**
     * 1440 슬롯(00:00~23:59)을 모두 생성하고 today/comparison 지표를 시간:분 키로 머지한다.
     *
     * <p>슬롯 순서대로(0~1439) 생성하므로 시간 오름차순 정렬이 자명 — 별도 sort 불필요.
     * 어느 한쪽이라도 결측인 슬롯은 해당 컬럼만 null 로 채운다 (PLAN1 가정 — 1440 고정).</p>
     */
    private List<TimeSeriesPoint> buildOptionTSeries(
            Map<String, MinuteMetrics> todayByTime,
            Map<String, MinuteMetrics> comparisonByTime) {
        List<TimeSeriesPoint> result = new ArrayList<>(MINUTES_PER_DAY);
        for (int min = 0; min < MINUTES_PER_DAY; min++) {
            String time = hhmm(min / 60, min % 60);
            result.add(mergeIntoSlot(time, todayByTime.get(time), comparisonByTime.get(time)));
        }
        return result;
    }

    /** 단일 시간:분 슬롯 머지 — 결측 측은 null. */
    private TimeSeriesPoint mergeIntoSlot(String time, MinuteMetrics today, MinuteMetrics comparison) {
        BigDecimal todayElpwrUnitQty = today != null ? today.elpwrUnitQty() : null;
        Integer todayOnPumpCnt = today != null ? today.onPumpCnt() : null;
        BigDecimal comparisonElpwrUnitQty = comparison != null ? comparison.elpwrUnitQty() : null;
        return TimeSeriesPoint.of(time, todayElpwrUnitQty, comparisonElpwrUnitQty, todayOnPumpCnt);
    }

    /** 시·분을 "HH:mm" 문자열(2자리 0 패딩)로 포맷 — 1440 슬롯 머지 키. */
    private String hhmm(int hour, int minute) {
        return String.format("%02d:%02d", hour, minute);
    }

    /**
     * 지정 시점 범위의 시계열을 시간:분(HH:mm) → {@link MinuteMetrics} Map 으로 변환.
     *
     * <p>Repository 단일 호출로 범위 내 모든 측정값을 일괄 조회한 뒤 {@code acq_dtm} 기준 grouping +
     * 시점별 운영 지표 계산. 단일 날짜 범위이므로 HH:mm 키 충돌은 없다. 데이터 0건 시 빈 Map.</p>
     */
    private Map<String, MinuteMetrics> collectMinuteMetrics(
            Map<String, List<Tag>> tagsByInstrument,
            List<Instrument> instruments,
            TimeRange range) {
        List<String> tagSrlNos = tagsByInstrument.values().stream()
                .flatMap(List::stream)
                .map(Tag::getTagSrlNo)
                .toList();
        if (tagSrlNos.isEmpty()) {
            return Map.of();
        }
        List<RawDataLatestDto> rows = rawDataRepository
                .findByTagSrlNosAndDtmRange(tagSrlNos, range.start(), range.end());
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<LocalDateTime, List<RawDataLatestDto>> rowsByDtm = rows.stream()
                .collect(Collectors.groupingBy(RawDataLatestDto::acqDtm));
        Map<String, MinuteMetrics> result = new HashMap<>();
        for (Map.Entry<LocalDateTime, List<RawDataLatestDto>> e : rowsByDtm.entrySet()) {
            String time = hhmm(e.getKey().getHour(), e.getKey().getMinute());
            result.put(time, computeMinuteMetrics(e.getValue(), tagsByInstrument, instruments));
        }
        return result;
    }

    /**
     * 단일 1분 시점의 운영 지표를 계산한다 — On 펌프 대수·전력원단위.
     *
     * <p>해당 시점에 측정된 raw 행들을 태그 시리얼번호 기준 map 화 → 4번 섹션 헬퍼 정책으로 변환.
     * 전력 합산값(PWI)은 전력원단위 계산의 중간 산물로만 사용하며 응답에 노출하지 않는다 (옵션 T).</p>
     */
    private MinuteMetrics computeMinuteMetrics(
            List<RawDataLatestDto> rowsAtThisMinute,
            Map<String, List<Tag>> tagsByInstrument,
            List<Instrument> instruments) {
        Map<String, RawDataLatestDto> latestByTag = rowsAtThisMinute.stream()
                .collect(Collectors.toMap(RawDataLatestDto::tagSrlNo, r -> r));
        List<Pump> pumps = filterPumps(instruments);
        List<Pump> onPumps = pumps.stream()
                .filter(p -> isPumpRunning(
                        pickLatest(tagsByInstrument, p.getInstrumentId(),
                                TagMeasurementType.OPS, latestByTag)))
                .toList();
        BigDecimal totalElpwrAmt = sumOnPumpPwr(onPumps, tagsByInstrument, latestByTag);
        RawDataLatestDto fri = selectFacilityFri(instruments, tagsByInstrument, latestByTag);
        BigDecimal elpwrUnitQty = computeUnitConsumption(totalElpwrAmt, fri);
        return new MinuteMetrics(onPumps.size(), elpwrUnitQty);
    }

    /**
     * 활성 시설 검증 + 본 카드 지원 시설 종류(PWTF·DWT·PRSF) 필터 — 4번 섹션 동일 정책 재구현.
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

    /** 시설 직속 계측기들의 활성 태그를 OPS/PWI/FRI 필터 후 인스트루먼트별로 그룹화 — 4번 동일. */
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

    /** 인스트루먼트의 지정 측정 유형 최신값 1건 추출 (없으면 null) — 4번 동일. */
    private RawDataLatestDto pickLatest(
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

    /** SCADA 값 선택 정책 — corrVal 우선, NULL 시 rawVal (4번 동일). */
    private BigDecimal effectiveVal(RawDataLatestDto r) {
        if (r == null) {
            return null;
        }
        return r.corrVal() != null ? r.corrVal() : r.rawVal();
    }

    /**
     * 펌프 On 판정 — null OR qualityCd != GOOD OR effectiveVal != 1.0 → false (4번 동일).
     *
     * <p>UNCERTAIN OPS 도 On 제외 — {@code ot-integration.md §3} OPS BAD 즉시 격상 정책의
     * 보수적 확장.</p>
     */
    private boolean isPumpRunning(RawDataLatestDto ops) {
        if (ops == null || ops.qualityCd() != QualityCode.GOOD) {
            return false;
        }
        BigDecimal val = effectiveVal(ops);
        return val != null && val.compareTo(BigDecimal.ONE) == 0;
    }

    /**
     * On 펌프들의 PWI 합산 — GOOD 만 합산, null/BAD/UNCERTAIN 전액 제외 (4번 동일).
     * On 펌프 0대 또는 모든 PWI 부재 시 BigDecimal.ZERO 반환.
     */
    private BigDecimal sumOnPumpPwr(
            List<Pump> onPumps,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        return onPumps.stream()
                .map(p -> pickLatest(tagsByInstrument, p.getInstrumentId(),
                        TagMeasurementType.PWI, latestByTag))
                .filter(pwi -> pwi != null && pwi.qualityCd() == QualityCode.GOOD)
                .map(this::effectiveVal)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** 시설 직속 FLWMTR 의 첫 GOOD FRI 태그 측정값 — 4번 동일 (parent_facility_id 재귀 미적용). */
    private RawDataLatestDto selectFacilityFri(
            List<Instrument> instruments,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.FLWMTR)
                .map(i -> pickLatest(tagsByInstrument, i.getInstrumentId(),
                        TagMeasurementType.FRI, latestByTag))
                .filter(r -> r != null && r.qualityCd() == QualityCode.GOOD)
                .findFirst()
                .orElse(null);
    }

    /**
     * 전력원단위 (kWh/m³) — 분자/분모 0/NULL/BAD/부재 4 케이스 모두 null (4번 동일).
     * 정상 시 totalElpwrAmt / friVal (scale=4, RoundingMode.HALF_UP).
     */
    private BigDecimal computeUnitConsumption(BigDecimal totalElpwrAmt, RawDataLatestDto fri) {
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
}
