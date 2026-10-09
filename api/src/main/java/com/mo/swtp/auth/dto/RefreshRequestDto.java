package com.mo.swtp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 토큰 갱신 요청 DTO.
 */
@Data
@NoArgsConstructor
@Schema(description = "토큰 갱신 요청 DTO")
public class RefreshRequestDto {

    @NotBlank
    @Schema(description = "리프레시 토큰")
    private String refreshToken;
}
