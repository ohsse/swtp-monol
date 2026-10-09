package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.mo.swtp.common.dto.BaseAuditResponseDto;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.ActivatedCarbonFilter;
import com.mo.swtp.facility.domain.ChemicalBuilding;
import com.mo.swtp.facility.domain.DewateringBuilding;
import com.mo.swtp.facility.domain.DistributionWaterTank;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.FiltrationBuilding;
import com.mo.swtp.facility.domain.PreOzonationBuilding;
import com.mo.swtp.facility.domain.PressureBoosterStation;
import com.mo.swtp.facility.domain.PurifiedWaterTank;
import com.mo.swtp.facility.domain.Reservoir;
import com.mo.swtp.facility.domain.SensorPoint;
import com.mo.swtp.facility.domain.SolarPowerFacility;
import com.mo.swtp.facility.domain.WaterTransmissionBuilding;
import com.mo.swtp.facility.domain.enumtype.FacilityGroup;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

/**
 * 시설 응답 추상 부모 DTO — JPA JOINED 다형성 ({@link Facility}) 의 응답 측 다형성 진입점.
 *
 * <p>{@link JsonTypeInfo} + {@link JsonSubTypes} 어노테이션으로 {@code facilityTypeCd} 필드값에 따라
 * 자식 DTO 12종 ({@link DwtDto}·{@link PwtfDto}·{@link RsvDto}·{@link PrsfDto}·{@link PointDto} +
 * 시설_도메인_확장 신규 7종 {@link WtbldDto}·{@link ChmbDto}·{@link AcfbDto}·{@link PozbDto}·
 * {@link FltbDto}·{@link DewbDto}·{@link SolarDto}) 으로 직렬화된다 (응답측 12종 — POINT 포함).
 * Request 측 {@link FacilityUpsertDto} 와 동일한 {@code EXISTING_PROPERTY} 다형성 패턴으로 응답·요청 대칭.</p>
 *
 * <p>자식 전용 필드 ({@code minReqPrsr} 등) 는 자식 DTO 에만 선언한다 — 부모 DTO 가 자식 필드를 노출하는
 * 패턴은 시설물응답DTO명세 ANALYZE1 (2026-05-12) 안건 1 결정으로 금지 ({@code api-patterns.md §상속 상한
 * — 2단 (마스터 다형성 한정 3단 예외)}).</p>
 *
 * <p>정적 팩토리 {@link #from(Facility)} 는 자식 엔티티 타입 매칭으로 자식 DTO 인스턴스를 반환한다 —
 * Service 계층 분기 책임 회피 ({@code entity-patterns.md §JPA JOINED + DiscriminatorColumn 다형성 패턴
 * §응답 DTO 매핑 패턴}).</p>
 *
 * <p>본 추상 부모는 {@link BaseAuditResponseDto} 의 메타 4컬럼 ({@code rgstrDtm}·{@code updtDtm}·
 * {@code rgstrId}·{@code updtId}) 을 상속하며, 자식 정적 팩토리에서 {@link #applyCommonFields(Facility)}
 * 헬퍼로 부모 공통 필드 + 메타 4컬럼을 일괄 주입한다.</p>
 *
 * <p>시설물응답DTO명세 ANALYZE1·PLAN1 (2026-05-12) 도입 — 종전 단일 구상 클래스에서 abstract 로 리팩토링.</p>
 */
@Getter
@Schema(
        description = "시설 응답 DTO — facilityTypeCd 값에 따라 자식 스키마 결정",
        oneOf = {
                DwtDto.class, PwtfDto.class, RsvDto.class, PrsfDto.class, PointDto.class,
                WtbldDto.class, ChmbDto.class, AcfbDto.class, PozbDto.class,
                FltbDto.class, DewbDto.class, SolarDto.class
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
        @JsonSubTypes.Type(value = DwtDto.class, name = "DWT"),
        @JsonSubTypes.Type(value = PwtfDto.class, name = "PWTF"),
        @JsonSubTypes.Type(value = RsvDto.class, name = "RSV"),
        @JsonSubTypes.Type(value = PrsfDto.class, name = "PRSF"),
        @JsonSubTypes.Type(value = PointDto.class, name = "POINT"),
        @JsonSubTypes.Type(value = WtbldDto.class, name = "WTBLD"),
        @JsonSubTypes.Type(value = ChmbDto.class, name = "CHMB"),
        @JsonSubTypes.Type(value = AcfbDto.class, name = "ACFB"),
        @JsonSubTypes.Type(value = PozbDto.class, name = "POZB"),
        @JsonSubTypes.Type(value = FltbDto.class, name = "FLTB"),
        @JsonSubTypes.Type(value = DewbDto.class, name = "DEWB"),
        @JsonSubTypes.Type(value = SolarDto.class, name = "SOLAR")
})
public abstract class FacilityDto extends BaseAuditResponseDto {

    @Schema(description = "시설 ID (UUID 자동 생성)", example = "550e8400-e29b-41d4-a716-446655440000")
    private String facilityId;

    @Schema(description = "시설명", example = "정수지1")
    private String facilityNm;

    @Schema(description = "시설 유형 코드 (PWTF/DWT/RSV/POINT/PRSF/WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR — discriminator)", implementation = FacilityType.class)
    private FacilityType facilityTypeCd;

    @Schema(description = "시설 그룹 코드 (STORAGE/OPERATION/NETWORK — facilityTypeCd 로부터 파생, DB 컬럼 아님)", implementation = FacilityGroup.class)
    private FacilityGroup facilityGroupCd;

    @Schema(description = "상위 시설 ID (NULL 허용)", example = "550e8400-e29b-41d4-a716-446655440001")
    private String parentFacilityId;

    @Schema(description = "표시 순서", example = "1")
    private Integer dispOrd;

    @Schema(description = "주요 시설 여부", implementation = YnType.class)
    private YnType mainYn;

    @Schema(description = "사용 여부", implementation = YnType.class)
    private YnType useYn;

    /**
     * 부모 공통 필드 + {@link BaseAuditResponseDto} 메타 4컬럼을 자식 DTO 에 일괄 주입한다.
     *
     * <p>자식 DTO 의 정적 팩토리 ({@code from(Child)}) 내부에서 호출한다 — 자식이 부모 필드를 setter
     * 호출이나 생성자 인자로 직접 받는 방식 금지 ({@code entity-patterns.md §응답 DTO 매핑 패턴}).</p>
     *
     * @param facility 부모 공통 필드 + 감사 메타를 보유한 {@link Facility} 자식 엔티티
     */
    protected void applyCommonFields(Facility facility) {
        this.facilityId = facility.getFacilityId();
        this.facilityNm = facility.getFacilityNm();
        this.facilityTypeCd = facility.getFacilityType();
        this.facilityGroupCd = facility.getFacilityType().getGroup();
        this.parentFacilityId = facility.getParentFacilityId();
        this.dispOrd = facility.getDispOrd();
        this.mainYn = facility.getMainYn();
        this.useYn = facility.getUseYn();
        applyAuditMeta(facility);
    }

    /**
     * 시설 엔티티의 자식 타입에 매칭되는 자식 응답 DTO 를 생성한다.
     *
     * <p>Java 21 switch 패턴 매칭으로 자식 엔티티 타입을 분기한다. 12 자식 종류 외 자식이 등장하면
     * {@link IllegalStateException} — sealed class 미사용이므로 컴파일러 exhaustiveness 미보장,
     * runtime 검증으로 fail-fast.</p>
     *
     * @param facility 자식 엔티티 (JPA JOINED 다형성)
     * @return 자식 타입에 대응하는 자식 응답 DTO 인스턴스
     * @throws IllegalStateException 알 수 없는 자식 종류
     */
    public static FacilityDto from(Facility facility) {
        return switch (facility) {
            case DistributionWaterTank dwt -> DwtDto.from(dwt);
            case PurifiedWaterTank pwtf -> PwtfDto.from(pwtf);
            case Reservoir rsv -> RsvDto.from(rsv);
            case PressureBoosterStation prsf -> PrsfDto.from(prsf);
            case SensorPoint point -> PointDto.from(point);
            case WaterTransmissionBuilding wtbld -> WtbldDto.from(wtbld);
            case ChemicalBuilding chmb -> ChmbDto.from(chmb);
            case ActivatedCarbonFilter acfb -> AcfbDto.from(acfb);
            case PreOzonationBuilding pozb -> PozbDto.from(pozb);
            case FiltrationBuilding fltb -> FltbDto.from(fltb);
            case DewateringBuilding dewb -> DewbDto.from(dewb);
            case SolarPowerFacility solar -> SolarDto.from(solar);
            default -> throw new IllegalStateException(
                    "Unknown facility subtype: " + facility.getClass().getName());
        };
    }
}
