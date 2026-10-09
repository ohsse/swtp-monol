package com.mo.swtp.auth.service;

import com.mo.swtp.auth.domain.RefreshToken;
import com.mo.swtp.auth.repository.RefreshTokenRepository;
import com.mo.swtp.common.exception.JwtErrorCode;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.common.jwt.JwtToken;
import com.mo.swtp.common.jwt.JwtTokenHelper;
import com.mo.swtp.common.jwt.JwtTokenInspection;
import com.mo.swtp.common.jwt.JwtTokenStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * JWT 토큰 발급·회전·폐기·검증 서비스.
 *
 * <p>리프레시 토큰의 등록자·수정자는 JPA Auditing 이 자동 주입한다.</p>
 */
@Service
@Transactional(readOnly = true)
public class JwtTokenManagementService {

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtTokenHelper jwtTokenHelper;

    /**
     * JWT 토큰 관리 서비스를 생성한다.
     *
     * @param refreshTokenRepository 리프레시 토큰 저장소
     * @param jwtTokenHelper         JWT 토큰 헬퍼
     */
    public JwtTokenManagementService(
            RefreshTokenRepository refreshTokenRepository,
            JwtTokenHelper jwtTokenHelper
    ) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtTokenHelper = jwtTokenHelper;
    }

    /**
     * Access Token / Refresh Token 쌍을 발급한다.
     *
     * @param subject 토큰 주체 (userId)
     * @param claims  추가 클레임
     * @return 발급된 토큰 쌍
     */
    @Transactional
    public JwtToken issueTokens(String subject, Map<String, Object> claims) {
        JwtToken tokenPair = jwtTokenHelper.generateTokenPair(subject, claims);
        String refreshTokenHash = hashToken(tokenPair.getRefreshToken());
        LocalDateTime refreshTokenExpiresAt = toLocalDateTime(jwtTokenHelper.inspect(tokenPair.getRefreshToken()).expiresAt());

        RefreshToken refreshToken = refreshTokenRepository.findByUserId(subject)
                .map(existing -> {
                    existing.rotate(refreshTokenHash, refreshTokenExpiresAt, null);
                    return existing;
                })
                .orElseGet(() -> RefreshToken.create(subject, refreshTokenHash, refreshTokenExpiresAt));

        refreshTokenRepository.save(refreshToken);
        return tokenPair;
    }

    /**
     * 리프레시 토큰을 회전하여 새 AT/RT 쌍을 발급한다.
     *
     * @param refreshToken 클라이언트가 제출한 리프레시 토큰 원본
     * @return 새로 발급된 토큰 쌍
     * @throws RestApiException REFRESH_TOKEN_NOT_FOUND — 저장된 토큰 없음
     * @throws RestApiException REFRESH_TOKEN_REVOKED — 이미 폐기된 토큰
     * @throws RestApiException EXPIRED_TOKEN — 만료된 토큰
     * @throws RestApiException REFRESH_TOKEN_MISMATCH — 해시 불일치
     */
    @Transactional
    public JwtToken rotateRefreshToken(String refreshToken) {
        JwtTokenInspection inspection = validateRefreshToken(refreshToken);
        String subject = inspection.subject();
        String inputTokenHash = hashToken(refreshToken);

        RefreshToken storedToken = refreshTokenRepository.findByUserId(subject)
                .orElseThrow(() -> new RestApiException(JwtErrorCode.REFRESH_TOKEN_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        if (storedToken.isRevoked()) {
            throw new RestApiException(JwtErrorCode.REFRESH_TOKEN_REVOKED);
        }
        if (storedToken.isExpired(now)) {
            throw new RestApiException(JwtErrorCode.EXPIRED_TOKEN);
        }
        if (!storedToken.getTokenHash().equals(inputTokenHash)) {
            throw new RestApiException(JwtErrorCode.REFRESH_TOKEN_MISMATCH);
        }

        JwtToken newTokenPair = jwtTokenHelper.generateTokenPair(subject, sanitizeClaims(inspection.claims()));
        storedToken.rotate(
                hashToken(newTokenPair.getRefreshToken()),
                toLocalDateTime(jwtTokenHelper.inspect(newTokenPair.getRefreshToken()).expiresAt()),
                now
        );
        refreshTokenRepository.save(storedToken);
        return newTokenPair;
    }

    /**
     * 리프레시 토큰을 폐기한다 (로그아웃).
     *
     * @param subject 토큰 주체 (userId)
     */
    @Transactional
    public void revokeRefreshToken(String subject) {
        refreshTokenRepository.findByUserId(subject)
                .ifPresent(token -> {
                    if (!token.isRevoked()) {
                        token.revoke(LocalDateTime.now());
                        refreshTokenRepository.save(token);
                    }
                });
    }

    /**
     * Access Token 을 검증하고 검사 결과를 반환한다.
     *
     * <p>서명·만료·타입에 더해 {@code sub} claim 의 비어있지 않음을 보장한다.
     * subject 는 후속 처리(JWT 필터의 request attribute 적재, 감사 컬럼 주입,
     * 이력 엔티티 발행자 기록) 의 단일 출처이므로 본 진입점에서 결함을 차단해야
     * 컨트롤러·서비스 단계의 중복 방어 코드를 제거할 수 있다.</p>
     *
     * @param token Access Token 원본
     * @return 토큰 검사 결과
     * @throws RestApiException EXPIRED_TOKEN — 만료됨
     * @throws RestApiException INVALID_TOKEN — 서명 불일치·타입 오류·sub claim 누락
     */
    public JwtTokenInspection validateAccessToken(String token) {
        JwtTokenInspection inspection = jwtTokenHelper.inspect(token);
        if (inspection.status() == JwtTokenStatus.EXPIRED) {
            throw new RestApiException(JwtErrorCode.EXPIRED_TOKEN);
        }
        if (inspection.status() == JwtTokenStatus.INVALID) {
            throw new RestApiException(JwtErrorCode.INVALID_TOKEN);
        }
        if (!JwtTokenHelper.ACCESS_TOKEN_TYPE.equals(inspection.claims().get(JwtTokenHelper.TOKEN_TYPE_CLAIM))) {
            throw new RestApiException(JwtErrorCode.INVALID_TOKEN);
        }
        if (inspection.subject() == null || inspection.subject().isBlank()) {
            throw new RestApiException(JwtErrorCode.INVALID_TOKEN);
        }
        return inspection;
    }

    private JwtTokenInspection validateRefreshToken(String refreshToken) {
        JwtTokenInspection inspection = jwtTokenHelper.inspect(refreshToken);
        if (inspection.status() == JwtTokenStatus.EXPIRED) {
            throw new RestApiException(JwtErrorCode.EXPIRED_TOKEN);
        }
        if (inspection.status() == JwtTokenStatus.INVALID) {
            throw new RestApiException(JwtErrorCode.INVALID_TOKEN);
        }
        if (!JwtTokenHelper.REFRESH_TOKEN_TYPE.equals(inspection.claims().get(JwtTokenHelper.TOKEN_TYPE_CLAIM))) {
            throw new RestApiException(JwtErrorCode.INVALID_TOKEN);
        }
        return inspection;
    }

    private Map<String, Object> sanitizeClaims(Map<String, Object> claims) {
        Map<String, Object> sanitized = new LinkedHashMap<>(claims);
        sanitized.remove(JwtTokenHelper.TOKEN_TYPE_CLAIM);
        sanitized.remove("iss");
        sanitized.remove("sub");
        sanitized.remove("exp");
        sanitized.remove("iat");
        sanitized.remove("jti");
        sanitized.remove("nbf");
        return sanitized;
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 algorithm is not available", exception);
        }
    }
}
