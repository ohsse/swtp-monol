package com.mo.swtp.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 로그인 요청 DTO.
 */
@Data
@NoArgsConstructor
@Schema(description = "로그인 요청 DTO")
public class LoginRequestDto {

    @NotBlank
    @Schema(description = "사용자 ID", example = "admin")
    private String userId;

    @NotBlank
    @Schema(description = "비밀번호 (평문)", example = "password123!")
    private String userPw;
}
