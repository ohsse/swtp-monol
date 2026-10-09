package com.mo.swtp.instrument.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 계측기 등록·수정 요청 추상 부모 DTO — Jackson 다형성 역직렬화 진입점.
 *
 * <p>{@link JsonTypeInfo} + {@link JsonSubTypes} 어노테이션으로 {@code equipTypeCd} 필드값에 따라
 * 자식 DTO 6종 ({@link PumpUpsertDto}·{@link ValveUpsertDto}·{@link FlowMeterUpsertDto}·
 * {@link PressureMeterUpsertDto}·{@link LevelMeterUpsertDto}·{@link PowerMeterUpsertDto}) 으로
 * 자동 역직렬화된다. 자식 6종 모두 본 API 등록 대상 — facility 의 POINT 자식 (SCADA 자동 생성)
 * 같은 예외 분기 없음.</p>
 *
 * <p>SpringDoc Swagger {@code @Schema(oneOf={...}, discriminatorProperty="equipTypeCd")} 명시 의무
 * — frontend SPEC 자동 추출에서 자식 종류별 요청 스키마를 {@code oneOf} 로 노출.</p>
 *
 * <p>계측기관리CRUD PLAN1·TASK1 (2026-05-12) 도입. facility 의 {@code FacilityUpsertDto} 패턴 재현.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(
        description = "계측기 등록·수정 요청 DTO — equipTypeCd 값에 따라 자식 스키마 결정",
        oneOf = {
                PumpUpsertDto.class,
                ValveUpsertDto.class,
                FlowMeterUpsertDto.class,
                PressureMeterUpsertDto.class,
                LevelMeterUpsertDto.class,
                PowerMeterUpsertDto.class
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
        @JsonSubTypes.Type(value = PumpUpsertDto.class, name = "PUMP"),
        @JsonSubTypes.Type(value = ValveUpsertDto.class, name = "VALVE"),
        @JsonSubTypes.Type(value = FlowMeterUpsertDto.class, name = "FLWMTR"),
        @JsonSubTypes.Type(value = PressureMeterUpsertDto.class, name = "PRSMTR"),
        @JsonSubTypes.Type(value = LevelMeterUpsertDto.class, name = "LVMTR"),
        @JsonSubTypes.Type(value = PowerMeterUpsertDto.class, name = "ELCMTR")
})
public abstract class InstrumentUpsertDto {

    @Schema(description = "장비 유형 코드 (discriminator)", implementation = EquipType.class,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "equipTypeCd 는 필수입니다.")
    private EquipType equipTypeCd;

    @Schema(description = "소속 시설 ID (UUID 36자)", example = "550e8400-e29b-41d4-a716-446655440000",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "facilityId 는 필수입니다.")
    @Size(max = 36, message = "facilityId 는 UUID 36자 이하여야 합니다.")
    private String facilityId;

    @Schema(description = "계측기명 — (facility_id, instrument_nm) 복합 UNIQUE",
            example = "송수펌프-001", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "instrumentNm 은 필수입니다.")
    @Size(max = 100, message = "instrumentNm 은 100자 이하여야 합니다.")
    private String instrumentNm;

    @Schema(description = "표시 순서", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "dispOrd 는 필수입니다.")
    private Integer dispOrd;
}
