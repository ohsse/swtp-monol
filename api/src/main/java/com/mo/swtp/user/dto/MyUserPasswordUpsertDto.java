package com.mo.swtp.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 본인 비밀번호 변경 요청 DTO.
 *
 * <p>현재 비밀번호 검증 후 새 비밀번호로 변경한다. 두 값 모두 평문이며,
 * 서버에서 BCrypt 인코딩 후 저장한다.</p>
 */
@Data
@NoArgsConstructor
@Schema(description = "본인 비밀번호 변경 요청 DTO")
public class MyUserPasswordUpsertDto {

    @NotBlank
    @Schema(description = "현재 비밀번호 (평문 — 서버에서 BCrypt 매칭 검증)", example = "old-password")
    private String currentPw;

    @NotBlank
    @Schema(description = "새 비밀번호 (평문 — 서버에서 BCrypt 인코딩)", example = "new-password")
    private String newPw;
}
