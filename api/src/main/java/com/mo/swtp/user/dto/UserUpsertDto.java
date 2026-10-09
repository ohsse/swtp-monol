package com.mo.swtp.user.dto;

import com.mo.swtp.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 사용자 등록/수정 요청 DTO.
 */
@Data
@NoArgsConstructor
@Schema(description = "사용자 등록/수정 요청 DTO")
public class UserUpsertDto {

    @NotBlank
    @Schema(description = "사용자 ID", example = "admin")
    private String userId;

    @NotBlank
    @Schema(description = "사용자 이름", example = "관리자")
    private String userNm;

    @NotBlank
    @Schema(description = "비밀번호 (평문 — 서버에서 BCrypt 인코딩)", example = "password123!")
    private String userPw;

    @NotNull
    @Schema(description = "권한 역할", implementation = UserRole.class)
    private UserRole userRole;
}
