package com.mo.swtp.facility.dto;

import com.mo.swtp.facility.domain.DistributionWaterTank;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;

/**
 * 배수지 응답 DTO — {@link DistributionWaterTank} 자식 ({@code facility_type_cd = 'DWT'}).
 *
 * <p>자식 전용 필드 2건 — {@code minReqPrsr} (최소 요구 압력) · {@code minReqBranchPrsr} (분기점 최소 요구 압력) —
 * 을 노출한다. 다른 자식 DTO 에는 부재한다. Jackson 다형성 직렬화 시 {@code "facilityTypeCd":"DWT"}
 * discriminator 값으로 본 자식 스키마가 결정된다.</p>
 *
 * <p>시설물응답DTO명세 ANALYZE1·PLAN1 (2026-05-12) 도입 — 종전 부모 {@link FacilityDto} 가
 * {@code instanceof} 분기로 채우던 패턴 폐기. 송수펌프제어분석-5번섹션 PLAN1 (2026-05-14) 에서 자식 전용
 * 컬럼 1→2 확장.</p>
 */
@Getter
@Schema(description = "배수지 응답 DTO")
public class DwtDto extends FacilityDto {

    @Schema(description = "최소 요구 압력 (kgf/cm²)", example = "2.5")
    private BigDecimal minReqPrsr;

    @Schema(description = "분기점 최소 요구 압력 (kgf/cm²)", example = "0.8")
    private BigDecimal minReqBranchPrsr;

    private DwtDto() {}

    /**
     * 배수지 엔티티로부터 응답 DTO 를 생성한다.
     *
     * @param dwt 배수지 엔티티
     * @return 부모 공통 필드 + 자식 전용 {@code minReqPrsr}·{@code minReqBranchPrsr} 을 포함하는 응답 DTO
     */
    public static DwtDto from(DistributionWaterTank dwt) {
        DwtDto dto = new DwtDto();
        dto.applyCommonFields(dwt);
        dto.minReqPrsr = dwt.getMinReqPrsr();
        dto.minReqBranchPrsr = dwt.getMinReqBranchPrsr();
        return dto;
    }
}
