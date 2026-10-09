package com.mo.swtp.opt.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 전력피크 목표값 저장 요청 DTO.
 *
 * <p>운전원이 입력하는 목표 피크 전력값(kW) 하나만 포함한다. {@code @Positive} 로 양수를 강제하여
 * 시드의 "미설정" sentinel 0 이 저장값으로 회수되지 않도록 한다 (PLAN1 §시드 초기값 결정).</p>
 */
@Data
@NoArgsConstructor
@Schema(description = "전력피크 목표값 저장 요청 DTO")
public class PeakTargetUpsertDto {

    @NotNull
    @Positive
    @Schema(description = "목표 피크 전력값 (kW) — 양수만 허용", example = "900.0000")
    private BigDecimal targetPeakElpwr;
}
