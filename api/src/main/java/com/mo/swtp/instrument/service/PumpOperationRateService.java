package com.mo.swtp.instrument.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.PumpDriveType;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.dto.PumpOperationRateDto;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.repository.RawDataRepository;
import com.mo.swtp.tag.domain.Tag;
import com.mo.swtp.tag.domain.enumtype.TagMeasurementType;
import com.mo.swtp.tag.repository.TagRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송수펌프 가동이력 2번섹션 — 펌프 상태 카드 조회 서비스.
 *
 * <p>전체 활성 송수펌프({@code equip_type_cd = 'PUMP'} + {@code use_yn = Y}) 의 제원값(정격양정·정격유량)과
 * 구동 방식별 가동률(%)을 카드 단위로 구성한다 (송수펌프가동이력_2번섹션 PLAN1 §구현 방향).</p>
 *
 * <p>4-SELECT 패턴 — Instrument 1 + Tag 1 + RawData 1 + 인메모리 산정 ({@code FacilityOperatingStatusService}
 * 동형, 인용 근거). 시계열 마스터 FK 금지 정책상 태그 → 최신값은 {@code tag_srl_no} 논리 참조로 IN 절 일괄
 * 조회한다. 추상화 깊이는 호출 스택 3단 이하 + 메서드 본문 50줄 이내 ({@code .claude/rules/coding-discipline.md §2.1}).</p>
 *
 * <p>도메인 룰 인용:
 * {@code .claude/rules/ot-integration.md §3} (OPS Hold Last Value 금지 — rawVal 단독 사용) ·
 * {@code .claude/rules/entity-patterns.md §JPA JOINED 다형성 §도메인 룰} ({@code equip_type_cd} 필터 강제).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PumpOperationRateService {

    /** 가동률 판정 입력 태그 유형 — OPS 가동상태(정격) / FQI 주파수(인버터). */
    private static final Set<TagMeasurementType> TARGET_TAG_TYPES =
            EnumSet.of(TagMeasurementType.OPS, TagMeasurementType.FQI);

    /** 정격 펌프 가동(On) 가동률 (%). */
    private static final BigDecimal RATE_ON = new BigDecimal("100");

    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    private final RawDataRepository rawDataRepository;

    /**
     * 전체 활성 송수펌프의 상태 카드를 조회한다.
     *
     * @return 펌프 상태 카드 목록 (표시 순서 → 계측기명 정렬). 활성 펌프 0대 시 빈 목록
     */
    public List<PumpOperationRateDto> findPumpOperationRates() {
        List<Instrument> pumps = instrumentRepository
                .findByEquipTypeAndUseYnOrderByDispOrdAscInstrumentNmAsc(EquipType.PUMP, YnType.Y);
        if (pumps.isEmpty()) {
            return List.of();
        }

        Map<String, List<Tag>> tagsByInstrument = loadTagsByInstrument(pumps);
        Map<String, RawDataLatestDto> latestByTag = loadLatestByTag(tagsByInstrument);

        return pumps.stream()
                .map(i -> (Pump) i)   // equip_type_cd = 'PUMP' 파생 쿼리 보장 — 안전 캐스팅
                .map(p -> toDto(p, tagsByInstrument, latestByTag))
                .toList();
    }

    /** 펌프들의 활성 태그를 OPS/FQI 필터 후 인스트루먼트별로 그룹화. */
    private Map<String, List<Tag>> loadTagsByInstrument(List<Instrument> pumps) {
        List<String> instrumentIds = pumps.stream().map(Instrument::getInstrumentId).toList();
        return tagRepository.findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(t -> TARGET_TAG_TYPES.contains(t.getTagSeCd()))
                .collect(Collectors.groupingBy(t -> t.getInstrument().getInstrumentId()));
    }

    /** 태그별 최신 측정값 일괄 조회 — IN 절 단일 쿼리. */
    private Map<String, RawDataLatestDto> loadLatestByTag(Map<String, List<Tag>> tagsByInstrument) {
        List<String> tagSrlNos = tagsByInstrument.values().stream()
                .flatMap(List::stream)
                .map(Tag::getTagSrlNo)
                .toList();
        return rawDataRepository.findLatestByTagSrlNos(tagSrlNos).stream()
                .collect(Collectors.toMap(RawDataLatestDto::tagSrlNo, r -> r));
    }

    /** 펌프 1대의 OPS·FQI 최신값을 추출해 카드 DTO 로 매핑. 판정 태그(정격=OPS, 인버터=FQI) 품질·수집시각 동봉. */
    private PumpOperationRateDto toDto(
            Pump pump,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, RawDataLatestDto> latestByTag) {
        RawDataLatestDto ops = pickLatest(tagsByInstrument, pump.getInstrumentId(),
                TagMeasurementType.OPS, latestByTag);
        RawDataLatestDto fqi = pickLatest(tagsByInstrument, pump.getInstrumentId(),
                TagMeasurementType.FQI, latestByTag);
        RawDataLatestDto decisionTag =
                pump.getDriveType() == PumpDriveType.INVERTER_DRIVE ? fqi : ops;

        BigDecimal oprtngRate = computeOprtngRate(pump, ops, fqi);
        QualityCode qualityCd = decisionTag != null ? decisionTag.qualityCd() : null;
        LocalDateTime acqDtm = decisionTag != null ? decisionTag.acqDtm() : null;
        return PumpOperationRateDto.of(pump, oprtngRate, qualityCd, acqDtm);
    }

    /** 인스트루먼트의 지정 측정 유형 최신값 1건 추출 (없으면 null). */
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

    /**
     * 구동 방식별 가동률(%) 산정 — nullable.
     *
     * <p>{@code RATED_DRIVE}: OPS {@code rawVal} 단독 사용 (Hold Last Value 금지 —
     * {@code .claude/rules/ot-integration.md §3}, {@code corr_val} 미사용). GOOD + 1.0 → 100, GOOD + 0.0 → 0,
     * 그 외(BAD/UNCERTAIN/null/0·1 외 값) → null.</p>
     *
     * <p>{@code INVERTER_DRIVE}: FQI GOOD → {@code effectiveVal}(corrVal ?? rawVal) 를 그대로 %
     * (정격주파수 정규화 미적용), 그 외 → null.</p>
     */
    private BigDecimal computeOprtngRate(Pump pump, RawDataLatestDto ops, RawDataLatestDto fqi) {
        if (pump.getDriveType() == PumpDriveType.INVERTER_DRIVE) {
            if (fqi == null || fqi.qualityCd() != QualityCode.GOOD) {
                return null;
            }
            return effectiveVal(fqi);
        }
        // RATED_DRIVE — OPS rawVal 단독 (Hold Last Value 금지)
        if (ops == null || ops.qualityCd() != QualityCode.GOOD) {
            return null;
        }
        BigDecimal raw = ops.rawVal();
        if (raw == null) {
            return null;
        }
        if (raw.compareTo(BigDecimal.ONE) == 0) {
            return RATE_ON;
        }
        if (raw.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.ZERO;
        }
        return null;   // 0·1 외 — 정의되지 않은 가동상태
    }

    /**
     * SCADA 값 선택 — corrVal 우선, NULL 시 rawVal. FQI(Hold Last Value 허용 측정유형) 전용 호출
     * ({@code .claude/rules/ot-integration.md §3} VOI 선례 동형).
     */
    private BigDecimal effectiveVal(RawDataLatestDto r) {
        if (r == null) {
            return null;
        }
        return r.corrVal() != null ? r.corrVal() : r.rawVal();
    }
}
