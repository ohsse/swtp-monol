package com.mo.swtp.proc.dto;

import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 운전모드 변경 요청 DTO.
 *
 * <p>{@code procId} 는 path variable 로 받으므로 본 DTO 에는 코드만 포함한다.</p>
 */
@Data
@NoArgsConstructor
@Schema(description = "AI 운전모드 변경 요청 DTO")
public class AiDrvnModeUpsertDto {

    @NotNull
    @Schema(description = "새 AI 운전모드 코드", implementation = AiDrvnModeCode.class)
    private AiDrvnModeCode aiDrvnModCd;
}
