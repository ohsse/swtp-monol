package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilityOutflowTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityOutflowTimeSeriesDto.LinePoint;
import com.mo.swtp.facility.dto.FacilityOutflowTimeSeriesDto.PumpPoint;
import com.mo.swtp.facility.dto.FacilityOutflowTimeSeriesDto.PumpSeries;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredcOutflowDto;
import com.mo.swtp.opt.repository.TagPredcOutflowRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataOutflowDto;
import com.mo.swtp.raw.repository.RawDataOutflowRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
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
 * 운전현황분석 7번 섹션 — 시설 단위 유출(송수) 유량·압력 + 펌프 가동상태 계측+예측 시계열 조회 서비스.
 *
 * <p>활성 시설({@code use_yn = Y}, 지원 종류 PWTF/DWT/PRSF) 의 금일 하루치({@code 00:00 ~ 현재시간}) 시계열을
 * 1분 단위로 응답한다. 계측·예측 모두 동일 구간({@code 금일 00:00 ~ now()}) — 사용자 결정 2026-06-01.
 * 응답은 두 시리즈로 분리된다:</p>
 * <ul>
 *   <li>라인 — 시설 직속 FLWMTR(첫 매치 고정) 의 유출 유량(FRI)·압력(PRI) 계측+예측 ({@link #buildLinePoints}).</li>
 *   <li>막대 — 시설 소속 펌프(PUMP) 별 OPS 가동상태 계측+예측 ({@link #buildPumpSeries}).</li>
 * </ul>
 *
 * <p>10번 섹션 {@link FacilityDailyTimeSeriesService} (전력원단위 단일 시계열) 와 관심사가 다르다 — 본 사이클은
 * 유량·압력·펌프 가동의 라인+막대 2-시리즈이며, 5·9·10번 헬퍼를 재사용하지 않고 본 서비스에 동일 정책으로
 * 재구현한다 (요청되지 않은 공통화 추상화 금지 — {@code coding-discipline.md §2·§3} · 사용자 메모리 "사이클 간
 * 자산 자동 원용 금지"). 5·9·10번 Service·Repository 는 무수정.</p>
 *
 * <p>OPS 가동상태는 계측·예측 테이블 원본값을 tri-state {@code Integer} ({@code 1}=가동 / {@code 0}=정지 /
 * {@code null}=불명) 로 그대로 노출한다 (Boolean true/false 변환 없음) — 10번 섹션의 2-state(가동/비가동) 판정과
 * 의도가 분리된다. on/off DI 신호이므로 BAD/UNCERTAIN·결측을 정지(0) 로 표시하면 가동 중 펌프를 정지로 오인할
 * 위험이 있어 불명(null) 으로 분리한다 ({@code ot-integration.md §3} OPS BAD 즉시 격상 정책 정합 —
 * 운전현황분석-7번섹션 ANALYZE1 안건 4).</p>
 *
 * <p>5-SELECT 패턴 — Facility 1 + Instrument 1 + Tag 1 + RawData 1 (actual 범위) + TagPredcOutflow 1
 * (predc 범위) = 5회. {@code equip_type_cd} 필터 강제 ({@code entity-patterns.md §JPA JOINED 다형성 §도메인 룰}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityOutflowTimeSeriesService {

    /** 본 카드 지원 시설 종류 — RSV(저수조 펌프 미보유) · POINT(관로 분기점) 거부 (5·9·10번 동일). */
    private static final Set<FacilityType> SUPPORTED_TYPES = EnumSet.of(
            FacilityType.PWTF, FacilityType.DWT, FacilityType.PRSF);

    /** 측정 대상 계측기 종류 — 펌프(OPS) + 유량계(FRI·PRI). */
    private static final List<EquipType> TARGET_EQUIP_TYPES = List.of(EquipType.PUMP, EquipType.FLWMTR);

    /** 측정 대상 태그 유형 — OPS 가동상태 / FRI 유출유량 / PRI 유출압력 (10번 대비 PWI 제거·PRI 추가). */
    private static final Set<TagMeasurementType> TARGET_TAG_TYPES = EnumSet.of(
            TagMeasurementType.OPS, TagMeasurementType.FRI, TagMeasurementType.PRI);

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataOutflowRepository rawDataOutflowRepository;
    private final TagPredcOutflowRepository tagPredcOutflowRepository;

    /**
     * 활성 시설의 금일 하루치 유출 유량·압력 라인 + 펌프 가동상태 막대 시계열을 응답한다.
     *
     * @param facilityId 활성 시설 ID
     * @return 시설 단위 금일 유출 시계열 (라인 + 펌프별 막대)
     * @throws RestApiException FACILITY_NOT_FOUND — 미존재 또는 비활성 시설
     * @throws RestApiException UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류
     */
    public FacilityOutflowTimeSeriesDto findFacilityOutflowTimeSeries(String facilityId) {
        Facility facility = findActiveFacilityOrThrow(facilityId);

        List<Instrument> instruments = instrumentRepository
                .findByFacilityIdAndEquipType(facilityId, TARGET_EQUIP_TYPES);
        if (instruments.isEmpty()) {
            return FacilityOutflowTimeSeriesDto.of(
                    facility.getFacilityId(), facility.getFacilityNm(), List.of(), List.of());
        }

        Map<String, List<Tag>> tagsByInstrument = loadTagsByInstrument(instruments);
        LocalDateTime startDtm = LocalDate.now().atStartOfDay();
        LocalDateTime endDtm = LocalDateTime.now();

        List<String> tagSrlNos = tagsByInstrument.values().stream()
                .flatMap(List::stream).map(Tag::getTagSrlNo).toList();
        List<RawDataOutflowDto> actuals = tagSrlNos.isEmpty() ? List.of()
                : rawDataOutflowRepository.findByTagSrlNosAndDtmRange(tagSrlNos, startDtm, endDtm);
        List<TagPredcOutflowDto> predcs = tagSrlNos.isEmpty() ? List.of()
                : tagPredcOutflowRepository.findByTagSrlNosAndPredcDtmRange(tagSrlNos, startDtm, endDtm);

        List<LinePoint> linePoints = buildLinePoints(actuals, predcs, tagsByInstrument, instruments);
        List<PumpSeries> pumpSeries = buildPumpSeries(actuals, predcs, tagsByInstrument, instruments);
        return FacilityOutflowTimeSeriesDto.of(
                facility.getFacilityId(), facility.getFacilityNm(), linePoints, pumpSeries);
    }

    /** 라인 1분 슬롯의 계측·예측 유량·압력 4 항목 보관 record — buildLinePoints 3단 분해의 중간 산출물. */
    private record LineParts(
            BigDecimal actualFlwrt,
            BigDecimal actualPrsr,
            BigDecimal predcFlwrt,
            BigDecimal predcPrsr) {
    }

    /** 펌프 1분 슬롯의 계측·예측 가동상태 원본값(0/1/null) 보관 record — buildPumpSeries 분해의 중간 산출물. */
    private record PumpParts(
            Integer actualOps,
            Integer predcOps) {
    }

    /**
     * 활성 시설 검증 + 본 카드 지원 시설 종류(PWTF·DWT·PRSF) 필터 — 5·9·10번 동일 정책 재구현.
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

    /** 시설 직속 계측기들의 활성 태그를 OPS/FRI/PRI 필터 후 인스트루먼트별로 그룹화 — 5·9·10번 동일. */
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

    /** 시설 직속 첫 매치 FLWMTR 1대 (findFirst 고정) — FRI·PRI 라인 일관성 보장 (slot 별 재선택 없음). */
    private Instrument selectPrimaryFlwmtr(List<Instrument> instruments) {
        return instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.FLWMTR)
                .findFirst()
                .orElse(null);
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

    /**
     * 시설 직속 FLWMTR 의 FRI(유량)·PRI(압력) 계측+예측 라인을 1분 {@link LinePoint} 목록으로 변환.
     *
     * <p>3단 분해: actual slot map 생성 → predc 슬롯 overlay → TreeMap 자연정렬 + 4값 부재 슬롯 생략.
     * FLWMTR 는 첫 매치 1회 고정 — FRI·PRI 가 동일 유량계 기준이 되도록 보장 (slot 별 재선택 없음).</p>
     */
    private List<LinePoint> buildLinePoints(
            List<RawDataOutflowDto> actuals,
            List<TagPredcOutflowDto> predcs,
            Map<String, List<Tag>> tagsByInstrument,
            List<Instrument> instruments) {
        Instrument flwmtr = selectPrimaryFlwmtr(instruments);
        if (flwmtr == null) {
            return List.of();
        }
        String friTag = pickTagSrlNo(tagsByInstrument, flwmtr.getInstrumentId(), TagMeasurementType.FRI);
        String priTag = pickTagSrlNo(tagsByInstrument, flwmtr.getInstrumentId(), TagMeasurementType.PRI);
        TreeMap<LocalDateTime, LineParts> map = new TreeMap<>();
        fillActualLineSlots(map, actuals, friTag, priTag);
        overlayPredcLineSlots(map, predcs, friTag, priTag);
        return toLinePoints(map);
    }

    /** actual 행을 시점별 grouping 하여 LineParts(actual 2 항목만 채움) TreeMap 생성 — GOOD 측정값만 채택. */
    private void fillActualLineSlots(
            TreeMap<LocalDateTime, LineParts> map,
            List<RawDataOutflowDto> actuals,
            String friTag,
            String priTag) {
        if (actuals.isEmpty()) {
            return;
        }
        Map<LocalDateTime, List<RawDataOutflowDto>> rowsByDtm = actuals.stream()
                .collect(Collectors.groupingBy(RawDataOutflowDto::acqDtm));
        for (Map.Entry<LocalDateTime, List<RawDataOutflowDto>> e : rowsByDtm.entrySet()) {
            Map<String, RawDataOutflowDto> byTag = e.getValue().stream()
                    .collect(Collectors.toMap(RawDataOutflowDto::tagSrlNo, r -> r, (a, b) -> a));
            BigDecimal flwrt = effectiveValGood(byTag.get(friTag));
            BigDecimal prsr = effectiveValGood(byTag.get(priTag));
            map.put(e.getKey(), new LineParts(flwrt, prsr, null, null));
        }
    }

    /** predc 행을 시점별 grouping 하여 기존 LineParts 의 predc 2 항목 갱신 또는 신규 슬롯 추가. */
    private void overlayPredcLineSlots(
            TreeMap<LocalDateTime, LineParts> map,
            List<TagPredcOutflowDto> predcs,
            String friTag,
            String priTag) {
        if (predcs.isEmpty()) {
            return;
        }
        Map<LocalDateTime, List<TagPredcOutflowDto>> rowsByDtm = predcs.stream()
                .collect(Collectors.groupingBy(TagPredcOutflowDto::predcDtm));
        for (Map.Entry<LocalDateTime, List<TagPredcOutflowDto>> e : rowsByDtm.entrySet()) {
            Map<String, TagPredcOutflowDto> byTag = e.getValue().stream()
                    .collect(Collectors.toMap(TagPredcOutflowDto::tagSrlNo, r -> r, (a, b) -> a));
            BigDecimal flwrt = predcLineVal(byTag.get(friTag));
            BigDecimal prsr = predcLineVal(byTag.get(priTag));
            LineParts prev = map.get(e.getKey());
            LineParts next = (prev == null)
                    ? new LineParts(null, null, flwrt, prsr)
                    : new LineParts(prev.actualFlwrt(), prev.actualPrsr(), flwrt, prsr);
            map.put(e.getKey(), next);
        }
    }

    /** TreeMap 자연정렬 결과를 LinePoint List 로 변환 — 4값(계측·예측 유량·압력) 모두 null 인 슬롯 생략. */
    private List<LinePoint> toLinePoints(TreeMap<LocalDateTime, LineParts> map) {
        List<LinePoint> result = new ArrayList<>(map.size());
        for (Map.Entry<LocalDateTime, LineParts> e : map.entrySet()) {
            LineParts p = e.getValue();
            if (p.actualFlwrt() == null && p.actualPrsr() == null
                    && p.predcFlwrt() == null && p.predcPrsr() == null) {
                continue;
            }
            result.add(LinePoint.of(e.getKey(),
                    p.actualFlwrt(), p.actualPrsr(), p.predcFlwrt(), p.predcPrsr()));
        }
        return result;
    }

    /**
     * 시설 소속 모든 펌프(PUMP) 의 OPS 가동상태 막대 시리즈를 펌프별로 구성.
     *
     * <p>OPS 태그·측정값이 없는 펌프도 빈 {@code points} 목록으로 포함된다 (전수 포함 — 차트 트랙 누락 방지).</p>
     */
    private List<PumpSeries> buildPumpSeries(
            List<RawDataOutflowDto> actuals,
            List<TagPredcOutflowDto> predcs,
            Map<String, List<Tag>> tagsByInstrument,
            List<Instrument> instruments) {
        return filterPumps(instruments).stream()
                .map(p -> buildSinglePumpSeries(p, actuals, predcs, tagsByInstrument))
                .toList();
    }

    /** 펌프 1대의 OPS 계측+예측 가동상태를 dtm-key TreeMap 으로 병합하여 막대 시리즈 1건 구성. */
    private PumpSeries buildSinglePumpSeries(
            Pump pump,
            List<RawDataOutflowDto> actuals,
            List<TagPredcOutflowDto> predcs,
            Map<String, List<Tag>> tagsByInstrument) {
        String opsTag = pickTagSrlNo(tagsByInstrument, pump.getInstrumentId(), TagMeasurementType.OPS);
        TreeMap<LocalDateTime, PumpParts> map = new TreeMap<>();
        groupActualOpsByDtm(map, actuals, opsTag);
        groupPredcOpsByDtm(map, predcs, opsTag);
        return PumpSeries.of(pump.getInstrumentId(), pump.getInstrumentNm(), toPumpPointList(map));
    }

    /** 펌프 OPS actual 행을 dtm 키로 원본값(0/1/null) 채움 — opsTag 부재 시 미수집 (빈 points 유발). */
    private void groupActualOpsByDtm(
            TreeMap<LocalDateTime, PumpParts> map,
            List<RawDataOutflowDto> actuals,
            String opsTag) {
        if (opsTag == null) {
            return;
        }
        for (RawDataOutflowDto row : actuals) {
            if (opsTag.equals(row.tagSrlNo())) {
                map.put(row.acqDtm(), new PumpParts(actualOpsTriState(row), null));
            }
        }
    }

    /** 펌프 OPS predc 행을 dtm 키로 원본값 갱신 또는 신규 추가 — actual 원본값 보존. */
    private void groupPredcOpsByDtm(
            TreeMap<LocalDateTime, PumpParts> map,
            List<TagPredcOutflowDto> predcs,
            String opsTag) {
        if (opsTag == null) {
            return;
        }
        for (TagPredcOutflowDto row : predcs) {
            if (opsTag.equals(row.tagSrlNo())) {
                PumpParts prev = map.get(row.predcDtm());
                Integer predcOps = predcOpsTriState(row);
                PumpParts next = (prev == null)
                        ? new PumpParts(null, predcOps)
                        : new PumpParts(prev.actualOps(), predcOps);
                map.put(row.predcDtm(), next);
            }
        }
    }

    /** TreeMap 자연정렬 결과를 PumpPoint List 로 변환 — 계측·예측 양쪽 불명(null) 슬롯 생략. */
    private List<PumpPoint> toPumpPointList(TreeMap<LocalDateTime, PumpParts> map) {
        List<PumpPoint> result = new ArrayList<>(map.size());
        for (Map.Entry<LocalDateTime, PumpParts> e : map.entrySet()) {
            PumpParts p = e.getValue();
            if (p.actualOps() == null && p.predcOps() == null) {
                continue;
            }
            result.add(PumpPoint.of(e.getKey(), p.actualOps(), p.predcOps()));
        }
        return result;
    }

    /** actual 값 선택 정책 — corrVal 우선, NULL 시 rawVal (5·10번 동일, QUALITY 무관). */
    private BigDecimal effectiveVal(RawDataOutflowDto r) {
        if (r == null) {
            return null;
        }
        return r.corrVal() != null ? r.corrVal() : r.rawVal();
    }

    /** 라인 계측값 — GOOD 만 채택 (BAD/UNCERTAIN·null → null), 채택 시 effectiveVal (5번 동형). */
    private BigDecimal effectiveValGood(RawDataOutflowDto r) {
        if (r == null || r.qualityCd() != QualityCode.GOOD) {
            return null;
        }
        return effectiveVal(r);
    }

    /** 라인 예측값 — null 안전 predcVal 추출 (predc_1m_h 는 QUALITY 컬럼 부재). */
    private BigDecimal predcLineVal(TagPredcOutflowDto r) {
        return r == null ? null : r.predcVal();
    }

    /**
     * actual 펌프 가동상태 원본값 — null·!GOOD → null(불명) / GOOD+1.0 → 1 / GOOD+0.0 → 0 / 그 외 → null.
     *
     * <p>Boolean true/false 변환 없이 계측 원본값 0/1 을 그대로 노출한다. 10번 섹션의 2-state(BAD→0) 와 분리 —
     * on/off DI 신호의 BAD/UNCERTAIN·결측을 정지(0) 로 표시하면 가동 중 펌프를 정지로 오인할 위험이 있어
     * 불명(null) 으로 분리한다 ({@code ot-integration.md §3} 정합).</p>
     */
    private Integer actualOpsTriState(RawDataOutflowDto ops) {
        if (ops == null || ops.qualityCd() != QualityCode.GOOD) {
            return null;
        }
        BigDecimal val = effectiveVal(ops);
        if (val == null) {
            return null;
        }
        if (val.compareTo(BigDecimal.ONE) == 0) {
            return 1;
        }
        if (val.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        return null;
    }

    /**
     * predc 펌프 가동상태 원본값 — null·predcVal null → null(불명) / 1.0 → 1 / 0.0 → 0 / 그 외 → null.
     *
     * <p>Boolean 변환 없이 예측 원본값 0/1 을 그대로 노출한다. {@code predc_1m_h} 는 {@code quality_cd}
     * 컬럼 부재 — SCADA QUALITY 분기 미적용.</p>
     */
    private Integer predcOpsTriState(TagPredcOutflowDto ops) {
        if (ops == null) {
            return null;
        }
        BigDecimal val = ops.predcVal();
        if (val == null) {
            return null;
        }
        if (val.compareTo(BigDecimal.ONE) == 0) {
            return 1;
        }
        if (val.compareTo(BigDecimal.ZERO) == 0) {
            return 0;
        }
        return null;
    }
}
