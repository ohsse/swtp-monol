package com.mo.swtp.common.jwt;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.Map;

@Schema(description = "JWT 검사 결과")
public record JwtTokenInspection(
        @Schema(description = "토큰 상태")
        JwtTokenStatus status,
        @Schema(description = "주체")
        String subject,
        @Schema(description = "클레임")
        Map<String, Object> claims,
        @Schema(description = "발급 시각")
        Instant issuedAt,
        @Schema(description = "만료 시각")
        Instant expiresAt
) {
}
