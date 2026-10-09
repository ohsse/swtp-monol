package com.mo.swtp.proc.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 공정/제어대상 등록/수정 요청 DTO.
 *
 * <p>{@code procId} 는 외부 할당 PK 이며 정규식 {@code ^[A-Z][A-Z0-9_]*$} 로 형식 검증된다.</p>
 */
@Data
@NoArgsConstructor
@Schema(description = "공정/제어대상 등록/수정 요청 DTO")
public class ProcUpsertDto {

    @NotBlank
    @Pattern(regexp = "^[A-Z][A-Z0-9_]*$", message = "procId 는 대문자로 시작하고 대문자·숫자·언더스코어만 허용됩니다")
    @Schema(description = "공정/제어대상 ID (외부 할당 — 대문자 시작 + 대문자·숫자·언더스코어)", example = "PUMP_CONTROL")
    private String procId;

    @NotBlank
    @Schema(description = "공정/제어대상명 (시스템 전체 UNIQUE)", example = "송수펌프제어")
    private String procNm;

    @NotNull
    @Schema(description = "표시 순서", example = "1")
    private Integer dispOrd;
}
