package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 시설 등록·수정 요청 추상 부모 DTO — Jackson 다형성 역직렬화 진입점.
 *
 * <p>{@link JsonTypeInfo} + {@link JsonSubTypes} 어노테이션으로 {@code facilityTypeCd} 필드값에 따라
 * 자식 DTO ({@link PwtfUpsertDto}·{@link DwtUpsertDto}·{@link RsvUpsertDto}·{@link PrsfUpsertDto} +
 * 시설_도메인_확장 신규 7종 {@link WtbldUpsertDto}·{@link ChmbUpsertDto}·{@link AcfbUpsertDto}·
 * {@link PozbUpsertDto}·{@link FltbUpsertDto}·{@link DewbUpsertDto}·{@link SolarUpsertDto}) 로
 * 자동 역직렬화된다 (요청측 11종 — POINT 제외). POINT 자식은 SCADA 도메인 자동 생성이므로 수동 등록 API
 * 대상 외 (시설물관리기능 ANALYZE1 사용자 결정 2026-05-11).</p>
 *
 * <p>SpringDoc Swagger {@code @Schema(oneOf={...}, discriminatorProperty="facilityTypeCd")} 명시 의무
 * — frontend SPEC 자동 추출에서 자식 종류별 요청 스키마를 {@code oneOf} 로 노출 (PLAN1 §다형성 DTO 어노테이션).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(
        description = "시설 등록·수정 요청 DTO — facilityTypeCd 값에 따라 자식 스키마 결정",
        oneOf = {
                PwtfUpsertDto.class, DwtUpsertDto.class, RsvUpsertDto.class, PrsfUpsertDto.class,
                WtbldUpsertDto.class, ChmbUpsertDto.class, AcfbUpsertDto.class, PozbUpsertDto.class,
                FltbUpsertDto.class, DewbUpsertDto.class, SolarUpsertDto.class
        },
        discriminatorProperty = "facilityTypeCd"
)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "facilityTypeCd",
        visible = true
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PwtfUpsertDto.class, name = "PWTF"),
        @JsonSubTypes.Type(value = DwtUpsertDto.class, name = "DWT"),
        @JsonSubTypes.Type(value = RsvUpsertDto.class, name = "RSV"),
        @JsonSubTypes.Type(value = PrsfUpsertDto.class, name = "PRSF"),
        @JsonSubTypes.Type(value = WtbldUpsertDto.class, name = "WTBLD"),
        @JsonSubTypes.Type(value = ChmbUpsertDto.class, name = "CHMB"),
        @JsonSubTypes.Type(value = AcfbUpsertDto.class, name = "ACFB"),
        @JsonSubTypes.Type(value = PozbUpsertDto.class, name = "POZB"),
        @JsonSubTypes.Type(value = FltbUpsertDto.class, name = "FLTB"),
        @JsonSubTypes.Type(value = DewbUpsertDto.class, name = "DEWB"),
        @JsonSubTypes.Type(value = SolarUpsertDto.class, name = "SOLAR")
})
public abstract class FacilityUpsertDto {

    @Schema(description = "시설 유형 코드 (discriminator)", implementation = FacilityType.class, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "facilityTypeCd 는 필수입니다.")
    private FacilityType facilityTypeCd;

    @Schema(description = "시설명 (시스템 전체 UNIQUE)", example = "정수지1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "facilityNm 은 필수입니다.")
    @Size(max = 100, message = "facilityNm 은 100자 이하여야 합니다.")
    private String facilityNm;

    @Schema(description = "상위 시설 ID (NULL 허용 — 최상위 시설)", example = "550e8400-e29b-41d4-a716-446655440001")
    @Size(max = 36, message = "parentFacilityId 는 UUID 36자 이하여야 합니다.")
    private String parentFacilityId;

    @Schema(description = "표시 순서", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "dispOrd 는 필수입니다.")
    private Integer dispOrd;

    @Schema(description = "주요 시설 여부", implementation = YnType.class, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "mainYn 은 필수입니다.")
    private YnType mainYn;
}
