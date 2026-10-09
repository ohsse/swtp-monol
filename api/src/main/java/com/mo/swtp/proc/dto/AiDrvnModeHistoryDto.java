package com.mo.swtp.proc.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.proc.domain.AiDrvnModeHistory;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * AI 운전모드 변경 이력 조회 응답 DTO.
 *
 * <p>{@code _h} 시계열 이력 — {@code BaseAuditResponseDto} 미상속 ({@code api-patterns.md} §적용 범위).
 * rgstrDtm·updtDtm·rgstrId·updtId 4 컬럼을 직접 선언한다 (BaseEntity 4 상속 엔티티에서 매핑).</p>
 */
@Getter
@NoArgsConstructor
@Schema(description = "AI 운전모드 변경 이력 조회 응답 DTO")
public class AiDrvnModeHistoryDto {

    @Schema(description = "이력 ID", example = "1")
    private Long aiDrvnModId;

    @Schema(description = "공정/제어대상 ID", example = "PUMP_CONTROL")
    private String procId;

    @Schema(description = "AI 운전모드 코드", implementation = AiDrvnModeCode.class)
    private AiDrvnModeCode aiDrvnModCd;

    @Schema(description = "모드 시작 일시", example = "2026-05-20 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime startDtm;

    @Schema(description = "모드 종료 일시 (NULL = 현재 활성)", example = "2026-05-20 12:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime endDtm;

    @Schema(description = "등록 일시", example = "2026-05-20 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime rgstrDtm;

    @Schema(description = "수정 일시", example = "2026-05-20 12:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime updtDtm;

    @Schema(description = "등록자 ID", example = "system")
    private String rgstrId;

    @Schema(description = "수정자 ID", example = "system")
    private String updtId;

    private AiDrvnModeHistoryDto(AiDrvnModeHistory history) {
        this.aiDrvnModId = history.getAiDrvnModId();
        this.procId = history.getProcId();
        this.aiDrvnModCd = history.getAiDrvnModCd();
        this.startDtm = history.getStartDtm();
        this.endDtm = history.getEndDtm();
        this.rgstrDtm = history.getRgstrDtm();
        this.updtDtm = history.getUpdtDtm();
        this.rgstrId = history.getRgstrId();
        this.updtId = history.getUpdtId();
    }

    /**
     * 이력 엔티티로부터 응답 DTO 를 생성한다.
     */
    public static AiDrvnModeHistoryDto from(AiDrvnModeHistory history) {
        return new AiDrvnModeHistoryDto(history);
    }
}
