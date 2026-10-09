package com.mo.swtp.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.crypto.SecretKey;

public class JwtTokenHelper {

    public static final String TOKEN_TYPE_CLAIM = "tokenType";
    public static final String ACCESS_TOKEN_TYPE = "access";
    public static final String REFRESH_TOKEN_TYPE = "refresh";

    private final SecretKey secretKey;
    private final String issuer;
    private final long accessTokenExpirationMinutes;
    private final long refreshTokenExpirationDays;

    public JwtTokenHelper(
            String secret,
            String issuer,
            long accessTokenExpirationMinutes,
            long refreshTokenExpirationDays
    ) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.issuer = issuer;
        this.accessTokenExpirationMinutes = accessTokenExpirationMinutes;
        this.refreshTokenExpirationDays = refreshTokenExpirationDays;
    }

    public JwtToken generateTokenPair(String subject, Map<String, Object> claims) {
        return JwtToken.builder()
                .accessToken(generateAccessToken(subject, claims))
                .refreshToken(generateRefreshToken(subject, claims))
                .build();
    }

    public String generateAccessToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, accessTokenExpirationMinutes, ChronoUnit.MINUTES, ACCESS_TOKEN_TYPE);
    }

    public String generateRefreshToken(String subject, Map<String, Object> claims) {
        return generateToken(subject, claims, refreshTokenExpirationDays, ChronoUnit.DAYS, REFRESH_TOKEN_TYPE);
    }

    public JwtTokenStatus getTokenStatus(String token) {
        return inspect(token).status();
    }

    public String getSubject(String token) {
        return inspect(token).subject();
    }

    public Map<String, Object> getClaims(String token) {
        return inspect(token).claims();
    }

    @SuppressWarnings("unchecked")
    public <T> T getClaim(String token, String claimName, Class<T> type) {
        Object claimValue = getClaims(token).get(claimName);
        if (claimValue == null) {
            return null;
        }
        if (type.isInstance(claimValue)) {
            return (T) claimValue;
        }
        if (type == String.class) {
            return (T) String.valueOf(claimValue);
        }
        throw new IllegalArgumentException("Unsupported claim type conversion: " + type.getName());
    }

    public JwtTokenInspection inspect(String token) {
        if (token == null || token.isBlank()) {
            return new JwtTokenInspection(JwtTokenStatus.INVALID, null, Collections.emptyMap(), null, null);
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return toInspection(JwtTokenStatus.VALID, claims);
        } catch (ExpiredJwtException exception) {
            return toInspection(JwtTokenStatus.EXPIRED, exception.getClaims());
        } catch (JwtException | IllegalArgumentException exception) {
            return new JwtTokenInspection(JwtTokenStatus.INVALID, null, Collections.emptyMap(), null, null);
        }
    }

    private String generateToken(
            String subject,
            Map<String, Object> claims,
            long expirationValue,
            ChronoUnit unit,
            String tokenType
    ) {
        Instant now = Instant.now();
        Instant expiration = now.plus(expirationValue, unit);

        Map<String, Object> mergedClaims = new LinkedHashMap<>();
        if (claims != null) {
            mergedClaims.putAll(claims);
        }
        mergedClaims.put(TOKEN_TYPE_CLAIM, tokenType);

        return Jwts.builder()
                .issuer(issuer)
                .subject(subject)
                .claims(mergedClaims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiration))
                .id(UUID.randomUUID().toString())
                .signWith(secretKey)
                .compact();
    }

    private JwtTokenInspection toInspection(JwtTokenStatus status, Claims claims) {
        Map<String, Object> copiedClaims = new LinkedHashMap<>(claims);
        return new JwtTokenInspection(
                status,
                claims.getSubject(),
                Collections.unmodifiableMap(copiedClaims),
                claims.getIssuedAt() == null ? null : claims.getIssuedAt().toInstant(),
                claims.getExpiration() == null ? null : claims.getExpiration().toInstant()
        );
    }
}
