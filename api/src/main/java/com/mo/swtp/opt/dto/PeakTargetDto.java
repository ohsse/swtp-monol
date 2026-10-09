package com.mo.swtp.opt.dto;

import com.mo.swtp.common.dto.BaseAuditResponseDto;
import com.mo.swtp.opt.domain.PeakTarget;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전력피크 목표값 조회·저장 응답 DTO.
 *
 * <p>{@link BaseAuditResponseDto} 상속으로 메타 4컬럼 자동 노출. PK ({@code peak_cd}) 는 항상
 * 동일 고정 코드값이라 클라이언트에 무의미하므로 응답에 노출하지 않는다 (PLAN1 §도메인 모델).</p>
 */
@Getter
@NoArgsConstructor
@Schema(description = "전력피크 목표값 조회·저장 응답 DTO")
public class PeakTargetDto extends BaseAuditResponseDto {

    @Schema(description = "목표 피크 전력값 (kW, 0 = 미설정 — 운전원 최초 저장 전)", example = "900.0000")
    private BigDecimal targetPeakElpwr;

    private PeakTargetDto(PeakTarget peakTarget) {
        this.targetPeakElpwr = peakTarget.getTargetPeakElpwr();
        applyAuditMeta(peakTarget);
    }

    /**
     * PeakTarget 엔티티로부터 응답 DTO 를 생성한다.
     */
    public static PeakTargetDto from(PeakTarget peakTarget) {
        return new PeakTargetDto(peakTarget);
    }
}
