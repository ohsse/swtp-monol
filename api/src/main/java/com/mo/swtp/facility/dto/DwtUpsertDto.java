package com.mo.swtp.facility.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 배수지(DWT) 등록·수정 요청 DTO.
 *
 * <p>자식 전용 컬럼 2건 — {@link #minReqPrsr}·{@link #minReqBranchPrsr} (모두 NOT NULL).
 * {@code minReqPrsr} 는 인터록 평가 기준값 (송수펌프제어분석 ANALYZE1, 2026-05-08).
 * {@code minReqBranchPrsr} 는 분기점 최소요구압력 (송수펌프제어분석-5번섹션 ANALYZE1, 2026-05-14) —
 * 본 사이클은 표출 전용이나 인터록 평가 편입 가능성 대비 NOT NULL.
 * pumpcontrol_null_alignment ANALYZE1 (2026-04-25) 결정 — DOM_QTY_15_4 기본 NULL 정책에서 더 엄격하게 적용
 * (인터록 평가 NULL/미입력 구별 불가 방지).</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Schema(description = "배수지(DWT) 등록·수정 요청 DTO — 자식 전용 minReqPrsr·minReqBranchPrsr (모두 NOT NULL) 보유")
public class DwtUpsertDto extends FacilityUpsertDto {

    @Schema(description = "최소 요구 압력 (kgf/cm²) — 인터록 평가 기준값", example = "2.5",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "minReqPrsr 은 필수입니다.")
    @Positive(message = "minReqPrsr 은 양수여야 합니다.")
    private BigDecimal minReqPrsr;

    @Schema(description = "분기점 최소 요구 압력 (kgf/cm²) — 배수지로 분기되는 관로 분기점 지점값",
            example = "0.8", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "minReqBranchPrsr 은 필수입니다.")
    @Positive(message = "minReqBranchPrsr 은 양수여야 합니다.")
    private BigDecimal minReqBranchPrsr;
}
