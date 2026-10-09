package com.mo.swtp.facility.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.dto.FacilityPredictionDto;
import com.mo.swtp.facility.dto.FlwmtrPredictionDto;
import com.mo.swtp.facility.dto.PumpPredictionDto;
import com.mo.swtp.facility.exception.FacilityErrorCode;
import com.mo.swtp.facility.repository.FacilityRepository;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import com.mo.swtp.instrument.repository.InstrumentRepository;
import com.mo.swtp.opt.dto.TagPredictionMatchDto;
import com.mo.swtp.opt.repository.TagPredictionRepository;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송수펌프제어분석 §7 — 시설 AI 예측 데이터 표출 서비스.
 *
 * <p>활성 시설({@code facilityId}, {@code use_yn = Y}) 의 유량계(FLWMTR) FRI/PRI 예측값 + 펌프(PUMP)
 * OPS 예측 가동상태 + 정적 {@code oprtngType} 을 단건 endpoint 응답으로 구성한다.
 * {@link FacilityStateService} 와 동일 4-step 골격이나 별도 클래스로 독립 유지 — 공통 추상화는
 * {@code coding-discipline.md §2} 위반 (PLAN1 §3 ANALYZE1 안건 4).</p>
 *
 * <p>섹션 7 전용 상수 {@code PREDICTION_EQUIP_TYPES}·{@code PREDICTION_TAG_TYPES} 는 섹션 3 의
 * {@code STATE_*} 와 별도 선언 — 섹션 3 자산 재사용 금지 (PLAN1 §3 + memory feedback —
 * {@code feedback_no_auto_reuse_cross_cycle}).</p>
 *
 * <p>4-step: Facility → Instrument → Tag → TagPrediction (근접매칭). SQL 4회.</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FacilityPredictionService {

    /** §7 예측 대상 계측기 종류 — 펌프 + 유량계 (PLAN1 §3 Step 2). */
    private static final List<EquipType> PREDICTION_EQUIP_TYPES =
            List.of(EquipType.PUMP, EquipType.FLWMTR);

    /** §7 예측 대상 태그 측정 유형 — FRI 유량 / PRI 압력 / OPS 가동상태 (PLAN1 §3 Step 3). */
    private static final Set<TagMeasurementType> PREDICTION_TAG_TYPES = EnumSet.of(
            TagMeasurementType.FRI, TagMeasurementType.PRI, TagMeasurementType.OPS);

    private final FacilityRepository facilityRepository;
    private final InstrumentRepository instrumentRepository;
    private final TagRepository tagRepository;
    /**
     * {@link TagPredictionRepository} (JpaRepository + CustomRepository 결합) 주입.
     * Custom 인터페이스 직접 주입 시 Spring 가 {@code tagPredictionCustomRepositoryImpl} +
     * {@code tagPredictionRepository} 두 빈을 모두 후보로 인식해 NoUniqueBeanDefinitionException 발생 —
     * 섹션 3 {@code FacilityStateService.rawDataRepository} 선례 정합으로 JpaRepository 주입 채택.
     */
    private final TagPredictionRepository tagPredictionRepository;

    /**
     * 근접매칭 윈도우(분). 1분 그리드 정합 기본 5분 (PLAN1 §2 ANALYZE1 미해결 #1 결정).
     * 하드코딩 금지 — {@code application-common.yml} {@code opt.prediction.match-window-minutes}.
     */
    @Value("${opt.prediction.match-window-minutes:5}")
    private int matchWindowMinutes;

    /**
     * 활성 시설의 AI 예측 데이터를 통합 응답한다.
     *
     * @param facilityId 활성 시설 ID
     * @return 시설 단위 유량계·펌프 예측 데이터
     * @throws RestApiException FACILITY_NOT_FOUND — 존재하지 않거나 비활성 시설
     */
    public FacilityPredictionDto findFacilityPrediction(String facilityId) {
        Facility facility = findActiveFacilityOrThrow(facilityId);

        List<Instrument> instruments = instrumentRepository
                .findByFacilityIdAndEquipType(facilityId, PREDICTION_EQUIP_TYPES);
        if (instruments.isEmpty()) {
            return FacilityPredictionDto.of(
                    facility.getFacilityId(), facility.getFacilityNm(),
                    List.of(), List.of());
        }

        List<String> instrumentIds = instruments.stream().map(Instrument::getInstrumentId).toList();
        List<Tag> tags = tagRepository
                .findByInstrumentInstrumentIdInAndUseYn(instrumentIds, YnType.Y).stream()
                .filter(t -> PREDICTION_TAG_TYPES.contains(t.getTagSeCd()))
                .toList();

        List<String> tagSrlNos = tags.stream().map(Tag::getTagSrlNo).toList();
        Map<String, TagPredictionMatchDto> predictionByTag = tagPredictionRepository
                .findNearestByTagSrlNos(tagSrlNos, matchWindowMinutes).stream()
                .collect(Collectors.toMap(TagPredictionMatchDto::tagSrlNo, p -> p));

        Map<String, List<Tag>> tagsByInstrument = tags.stream()
                .collect(Collectors.groupingBy(t -> t.getInstrument().getInstrumentId()));

        List<FlwmtrPredictionDto> flwmtrs = instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.FLWMTR)
                .map(i -> mapFlwmtrPrediction(i, tagsByInstrument, predictionByTag))
                .toList();
        List<PumpPredictionDto> pumps = instruments.stream()
                .filter(i -> i.getEquipType() == EquipType.PUMP)
                .map(i -> mapPumpPrediction((Pump) i, tagsByInstrument, predictionByTag))
                .toList();

        return FacilityPredictionDto.of(
                facility.getFacilityId(), facility.getFacilityNm(), flwmtrs, pumps);
    }

    /**
     * 활성 시설을 조회하거나 미존재·비활성 시 FACILITY_NOT_FOUND 예외를 던진다.
     *
     * <p>섹션 3 의 {@code FacilityStateService.findActiveFacilityOrThrow} 와 동일 형태이나 private 재구현 —
     * 섹션 3 private 헬퍼 직접 재사용 금지 (크로스사이클 경계 — PLAN1 §3 ANALYZE1 안건 4).</p>
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
     * 유량계 1대의 FRI/PRI 근접 예측값을 추출하여 응답 DTO 로 매핑한다.
     */
    private FlwmtrPredictionDto mapFlwmtrPrediction(
            Instrument flwmtr,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, TagPredictionMatchDto> predictionByTag) {
        List<Tag> instrumentTags = tagsByInstrument.getOrDefault(flwmtr.getInstrumentId(), List.of());
        TagPredictionMatchDto fri = pickPrediction(instrumentTags, TagMeasurementType.FRI, predictionByTag);
        TagPredictionMatchDto pri = pickPrediction(instrumentTags, TagMeasurementType.PRI, predictionByTag);
        return FlwmtrPredictionDto.of(
                flwmtr.getInstrumentId(), flwmtr.getInstrumentNm(),
                predcVal(fri), predcDtm(fri),
                predcVal(pri), predcDtm(pri));
    }

    /**
     * 펌프 1대의 OPS 근접 예측값 + 정적 oprtngType 을 추출하여 응답 DTO 로 매핑한다.
     */
    private PumpPredictionDto mapPumpPrediction(
            Pump pump,
            Map<String, List<Tag>> tagsByInstrument,
            Map<String, TagPredictionMatchDto> predictionByTag) {
        List<Tag> instrumentTags = tagsByInstrument.getOrDefault(pump.getInstrumentId(), List.of());
        TagPredictionMatchDto ops = pickPrediction(instrumentTags, TagMeasurementType.OPS, predictionByTag);
        Boolean predcIsRunning = toBoolean(predcVal(ops));
        return PumpPredictionDto.of(
                pump.getInstrumentId(), pump.getInstrumentNm(),
                pump.getOprtngType(),
                predcIsRunning, predcDtm(ops));
    }

    /**
     * 계측기 산하 태그 중 지정 측정 유형의 근접 예측값을 1건 추출한다.
     */
    private TagPredictionMatchDto pickPrediction(
            List<Tag> tags,
            TagMeasurementType targetType,
            Map<String, TagPredictionMatchDto> predictionByTag) {
        return tags.stream()
                .filter(t -> t.getTagSeCd() == targetType)
                .map(t -> predictionByTag.get(t.getTagSrlNo()))
                .filter(p -> p != null)
                .findFirst()
                .orElse(null);
    }

    private BigDecimal predcVal(TagPredictionMatchDto p) {
        return p == null ? null : p.predcVal();
    }

    private LocalDateTime predcDtm(TagPredictionMatchDto p) {
        return p == null ? null : p.predcDtm();
    }

    /**
     * OPS 예측 가동상태 predc_val Boolean 변환 — 1.0=true / 0.0=false / NULL 또는 그 외=null.
     * 섹션 3 {@code FacilityStateService.toBoolean} 미러 (별도 클래스 독립 — 헬퍼 재사용 금지).
     */
    private Boolean toBoolean(BigDecimal val) {
        if (val == null) {
            return null;
        }
        if (val.compareTo(BigDecimal.ONE) == 0) {
            return Boolean.TRUE;
        }
        if (val.compareTo(BigDecimal.ZERO) == 0) {
            return Boolean.FALSE;
        }
        return null;
    }
}
