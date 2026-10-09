package com.mo.swtp.auth.dto;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 토큰 발급/갱신 응답 DTO.
 */
@Getter
@Builder
@Schema(description = "토큰 발급/갱신 응답 DTO")
public class TokenResponseDto {

    @Schema(description = "액세스 토큰 (Bearer)")
    private String accessToken;

    @Schema(description = "리프레시 토큰")
    private String refreshToken;

    @Schema(description = "액세스 토큰 만료 일시")
    private LocalDateTime accessExprDtm;

    @Schema(description = "리프레시 토큰 만료 일시")
    private LocalDateTime refreshExprDtm;

    @Schema(description = "사용자 권한 역할", implementation = UserRole.class)
    private UserRole role;
    
    @Schema(description = "튜토리얼 사용 여부", implementation = YnType.class)
    private YnType ttrylUseYn;
}
