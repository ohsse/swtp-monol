package com.mo.swtp.auth.service;

import com.mo.swtp.auth.dto.TokenResponseDto;
import com.mo.swtp.auth.exception.AuthErrorCode;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.common.jwt.JwtToken;
import com.mo.swtp.common.jwt.JwtTokenHelper;
import com.mo.swtp.common.jwt.JwtTokenInspection;
import com.mo.swtp.user.domain.User;
import com.mo.swtp.user.domain.UserRole;
import com.mo.swtp.user.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 · 토큰 갱신 · 로그아웃 인증 서비스.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtTokenManagementService jwtTokenManagementService;
    private final JwtTokenHelper jwtTokenHelper;

    /**
     * 사용자 로그인 — 자격 증명을 검증하고 AT/RT 쌍을 발급한다.
     *
     * @param userId 사용자 ID
     * @param rawPw  평문 비밀번호
     * @return 발급된 토큰 응답 DTO
     * @throws RestApiException LOGIN_FAILED — 사용자가 없거나 비밀번호 불일치
     */
    @Transactional
    public TokenResponseDto login(String userId, String rawPw) {
        User user = userRepository.findByUserIdAndUseYn(userId, YnType.Y)
                .orElseThrow(() -> new RestApiException(AuthErrorCode.LOGIN_FAILED));

        if (!passwordEncoder.matches(rawPw, user.getUserPw())) {
            throw new RestApiException(AuthErrorCode.LOGIN_FAILED);
        }

        Map<String, Object> claims = Map.of("role", user.getUserRole().name());
        JwtToken tokenPair = jwtTokenManagementService.issueTokens(userId, claims);
        return buildTokenResponse(tokenPair, user.getUserRole(), user.getTtrylUseYn());
    }

    /**
     * 로그아웃 — 리프레시 토큰을 폐기한다.
     *
     * @param subject 인증된 사용자 subject (userId)
     */
    @Transactional
    public void logout(String subject) {
        jwtTokenManagementService.revokeRefreshToken(subject);
    }

    /**
     * 토큰 갱신 — RT를 회전하여 새 AT/RT 쌍을 발급한다.
     *
     * @param refreshToken 리프레시 토큰 원본값
     * @return 새로 발급된 토큰 응답 DTO
     */
    @Transactional
    public TokenResponseDto refresh(String refreshToken) {
        JwtToken newTokenPair = jwtTokenManagementService.rotateRefreshToken(refreshToken);

        String roleStr = (String) jwtTokenHelper.inspect(newTokenPair.getAccessToken())
                .claims()
                .get("role");
        
        YnType ttrylUseYn = (YnType) jwtTokenHelper.inspect(newTokenPair.getAccessToken())
                .claims()
                .get("ttrylUseYn");
        
        UserRole role = roleStr != null ? UserRole.valueOf(roleStr) : UserRole.USER;

        return buildTokenResponse(newTokenPair, role, ttrylUseYn);
    }

    private TokenResponseDto buildTokenResponse(JwtToken tokenPair, UserRole role, YnType ttrylUseYn) {
        JwtTokenInspection accessInspection = jwtTokenHelper.inspect(tokenPair.getAccessToken());
        JwtTokenInspection refreshInspection = jwtTokenHelper.inspect(tokenPair.getRefreshToken());

        return TokenResponseDto.builder()
                .accessToken(tokenPair.getAccessToken())
                .refreshToken(tokenPair.getRefreshToken())
                .accessExprDtm(toLocalDateTime(accessInspection.expiresAt()))
                .refreshExprDtm(toLocalDateTime(refreshInspection.expiresAt()))
                .role(role)
                .ttrylUseYn(ttrylUseYn)
                .build();
    }

    private LocalDateTime toLocalDateTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
