package com.mo.swtp.proc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.common.dto.BaseAuditResponseDto;
import com.mo.swtp.proc.domain.AiDrvnMode;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 운전모드 현재 상태 조회 응답 DTO.
 *
 * <p>{@link BaseAuditResponseDto} 상속으로 메타 4컬럼 자동 노출.</p>
 */
@Getter
@NoArgsConstructor
@Schema(description = "AI 운전모드 현재 상태 조회 응답 DTO")
public class AiDrvnModeDto extends BaseAuditResponseDto {

    @Schema(description = "공정/제어대상 ID", example = "PUMP_CONTROL")
    private String procId;

    @Schema(description = "AI 운전모드 코드", implementation = AiDrvnModeCode.class)
    private AiDrvnModeCode aiDrvnModCd;

    @Schema(description = "현재 모드 시작 일시", example = "2026-05-20 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startDtm;

    private AiDrvnModeDto(AiDrvnMode mode) {
        this.procId = mode.getProcId();
        this.aiDrvnModCd = mode.getAiDrvnModCd();
        this.startDtm = mode.getStartDtm();
        applyAuditMeta(mode);
    }

    /**
     * AiDrvnMode 엔티티로부터 응답 DTO 를 생성한다.
     */
    public static AiDrvnModeDto from(AiDrvnMode mode) {
        return new AiDrvnModeDto(mode);
    }
}
