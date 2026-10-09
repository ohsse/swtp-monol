package com.mo.swtp.instrument.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mo.swtp.common.dto.BaseAuditResponseDto;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.instrument.domain.FlowMeter;
import com.mo.swtp.instrument.domain.Instrument;
import com.mo.swtp.instrument.domain.LevelMeter;
import com.mo.swtp.instrument.domain.PowerMeter;
import com.mo.swtp.instrument.domain.PressureMeter;
import com.mo.swtp.instrument.domain.Pump;
import com.mo.swtp.instrument.domain.Valve;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 계측기 응답 추상 부모 DTO — JPA JOINED 다형성 ({@link Instrument}) 의 응답 측 다형성 진입점.
 *
 * <p>{@link JsonTypeInfo} + {@link JsonSubTypes} 어노테이션으로 {@code equipTypeCd} 필드값에 따라
 * 자식 DTO 6종 ({@link PumpDto}·{@link ValveDto}·{@link FlowMeterDto}·{@link PressureMeterDto}·
 * {@link LevelMeterDto}·{@link PowerMeterDto}) 으로 직렬화된다. Request 측 {@link InstrumentUpsertDto}
 * 와 동일한 {@code EXISTING_PROPERTY} 다형성 패턴으로 응답·요청 대칭.</p>
 *
 * <p>자식 5종 (Valve·FlowMeter·PressureMeter·LevelMeter·PowerMeter) 응답 DTO 는 자식 전용 컬럼 0건
 * skeleton 이지만 계측기관리CRUD 사이클 (2026-05-12) 의 자식 6종 단건 조회 API 요구로 본 사이클에서
 * 신설되었다 — 시설물응답DTO명세 ANALYZE1 (2026-05-12) 안건 6 "후속 사이클 이연" 후속.</p>
 *
 * <p>자식 전용 필드 ({@code ratedHead}·{@code ratedFlwrt}·{@code oprtngType}) 는 자식 DTO 에만 선언한다 —
 * 부모 DTO 가 자식 필드를 노출하는 패턴은 시설물응답DTO명세 ANALYZE1 안건 1 결정으로 금지
 * ({@code api-patterns.md §상속 상한 — 2단 (마스터 다형성 한정 3단 예외)}).</p>
 *
 * <p>{@code facility} 참조는 {@link #facilityId} 문자열만 노출한다 — 중첩 {@code FacilityDto}
 * 응답은 페이로드 비대 + frontend 별도 조회 가능 (PLAN1 §가정 및 미해결 질문 결정).</p>
 *
 * <p>시설물응답DTO명세 ANALYZE1·PLAN1 (2026-05-12) 도입.</p>
 */
@Getter
@Schema(
        description = "계측기 응답 DTO — equipTypeCd 값에 따라 자식 스키마 결정",
        oneOf = {
                PumpDto.class,
                ValveDto.class,
                FlowMeterDto.class,
                PressureMeterDto.class,
                LevelMeterDto.class,
                PowerMeterDto.class
        },
        discriminatorProperty = "equipTypeCd"
)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "equipTypeCd",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PumpDto.class, name = "PUMP"),
        @JsonSubTypes.Type(value = ValveDto.class, name = "VALVE"),
        @JsonSubTypes.Type(value = FlowMeterDto.class, name = "FLWMTR"),
        @JsonSubTypes.Type(value = PressureMeterDto.class, name = "PRSMTR"),
        @JsonSubTypes.Type(value = LevelMeterDto.class, name = "LVMTR"),
        @JsonSubTypes.Type(value = PowerMeterDto.class, name = "ELCMTR")
})
public abstract class InstrumentDto extends BaseAuditResponseDto {

    @Schema(description = "계측기 ID (UUID 자동 생성)", example = "550e8400-e29b-41d4-a716-446655440002")
    private String instrumentId;

    @Schema(description = "계측기명", example = "송수펌프-001")
    private String instrumentNm;

    @Schema(description = "장비 유형 코드 (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR — discriminator)", implementation = EquipType.class)
    private EquipType equipTypeCd;

    @Schema(description = "소속 시설 ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private String facilityId;

    @Schema(description = "표시 순서", example = "1")
    private Integer dispOrd;

    @Schema(description = "사용 여부", implementation = YnType.class)
    private YnType useYn;

    /**
     * 부모 공통 필드 + {@link BaseAuditResponseDto} 메타 4컬럼을 자식 DTO 에 일괄 주입한다.
     *
     * <p>자식 DTO 의 정적 팩토리 ({@code from(Child)}) 내부에서 호출한다.</p>
     *
     * @param instrument 부모 공통 필드 + 감사 메타를 보유한 {@link Instrument} 자식 엔티티
     */
    protected void applyCommonFields(Instrument instrument) {
        this.instrumentId = instrument.getInstrumentId();
        this.instrumentNm = instrument.getInstrumentNm();
        this.equipTypeCd = instrument.getEquipType();
        this.facilityId = instrument.getFacility() != null ? instrument.getFacility().getFacilityId() : null;
        this.dispOrd = instrument.getDispOrd();
        this.useYn = instrument.getUseYn();
        applyAuditMeta(instrument);
    }

    /**
     * 계측기 엔티티의 자식 타입에 매칭되는 자식 응답 DTO 를 생성한다.
     *
     * <p>Java 21 switch 패턴 매칭으로 자식 엔티티 타입을 분기한다. 6 자식 종류 외 자식이 등장하면
     * {@link IllegalStateException} — sealed class 미사용이므로 컴파일러 exhaustiveness 미보장,
     * runtime 검증으로 fail-fast.</p>
     *
     * @param instrument 자식 엔티티 (JPA JOINED 다형성)
     * @return 자식 타입에 대응하는 자식 응답 DTO 인스턴스
     * @throws IllegalStateException 알 수 없는 자식 종류
     */
    public static InstrumentDto from(Instrument instrument) {
        return switch (instrument) {
            case Pump p -> PumpDto.from(p);
            case Valve v -> ValveDto.from(v);
            case FlowMeter f -> FlowMeterDto.from(f);
            case PressureMeter pr -> PressureMeterDto.from(pr);
            case LevelMeter l -> LevelMeterDto.from(l);
            case PowerMeter pw -> PowerMeterDto.from(pw);
            default -> throw new IllegalStateException(
                    "Unknown instrument subtype: " + instrument.getClass().getName());
        };
    }
}
