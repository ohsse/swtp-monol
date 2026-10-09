package com.mo.swtp.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mo.swtp.auth.domain.RefreshToken;
import com.mo.swtp.auth.repository.RefreshTokenRepository;
import com.mo.swtp.common.exception.JwtErrorCode;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.common.jwt.JwtToken;
import com.mo.swtp.common.jwt.JwtTokenHelper;
import com.mo.swtp.common.jwt.JwtTokenInspection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class JwtTokenManagementServiceTest {

    private static final String SECRET = "local-dev-only-secret-change-me-0123456789";

    @Test
    void issuesAndValidatesAccessToken() {
        AtomicReference<RefreshToken> storedToken = new AtomicReference<>();
        JwtTokenManagementService service = new JwtTokenManagementService(
                inMemoryRepository(storedToken),
                new JwtTokenHelper(SECRET, "swtp", 30, 7)
        );

        JwtToken token = service.issueTokens("user-1", Map.of("role", "ADMIN"));
        JwtTokenInspection inspection = service.validateAccessToken(token.getAccessToken());

        assertEquals("user-1", inspection.subject());
        assertEquals("ADMIN", inspection.claims().get("role"));
    }

    @Test
    void rotatesRefreshTokenAndRejectsPreviousToken() {
        AtomicReference<RefreshToken> storedToken = new AtomicReference<>();
        JwtTokenManagementService service = new JwtTokenManagementService(
                inMemoryRepository(storedToken),
                new JwtTokenHelper(SECRET, "swtp", 30, 7)
        );

        JwtToken firstPair = service.issueTokens("user-1", Map.of("role", "ADMIN"));
        JwtToken secondPair = service.rotateRefreshToken(firstPair.getRefreshToken());

        assertNotEquals(firstPair.getRefreshToken(), secondPair.getRefreshToken());

        RestApiException exception = assertThrows(
                RestApiException.class,
                () -> service.rotateRefreshToken(firstPair.getRefreshToken())
        );
        assertEquals(JwtErrorCode.REFRESH_TOKEN_MISMATCH, exception.getErrorCode());
    }

    @Test
    void rejectsExpiredRefreshToken() {
        AtomicReference<RefreshToken> storedToken = new AtomicReference<>();
        JwtTokenManagementService service = new JwtTokenManagementService(
                inMemoryRepository(storedToken),
                new JwtTokenHelper(SECRET, "swtp", 30, -1)
        );

        JwtToken token = service.issueTokens("user-1", Map.of());

        RestApiException exception = assertThrows(
                RestApiException.class,
                () -> service.rotateRefreshToken(token.getRefreshToken())
        );

        assertEquals(JwtErrorCode.EXPIRED_TOKEN, exception.getErrorCode());
    }

    @Test
    void rejectsRefreshTokenWhenStoredValueIsRevoked() {
        AtomicReference<RefreshToken> storedToken = new AtomicReference<>();
        JwtTokenManagementService service = new JwtTokenManagementService(
                inMemoryRepository(storedToken),
                new JwtTokenHelper(SECRET, "swtp", 30, 7)
        );

        JwtToken token = service.issueTokens("user-1", Map.of());
        service.revokeRefreshToken("user-1");

        RestApiException exception = assertThrows(
                RestApiException.class,
                () -> service.rotateRefreshToken(token.getRefreshToken())
        );

        assertEquals(JwtErrorCode.REFRESH_TOKEN_REVOKED, exception.getErrorCode());
    }

    /**
     * sub claim 누락 토큰은 컨트롤러·서비스 단의 방어 코드 제거 전제 조건이다.
     * 본 테스트가 보장됨에 따라 {@code PumpControlController} 등 후속 처리 단계에서
     * subject null/blank 검증을 중복으로 두지 않는다.
     */
    @Test
    void validateAccessToken_subject_가_null_이면_INVALID_TOKEN_을_던진다() {
        AtomicReference<RefreshToken> storedToken = new AtomicReference<>();
        JwtTokenHelper helper = new JwtTokenHelper(SECRET, "swtp", 30, 7);
        JwtTokenManagementService service = new JwtTokenManagementService(
                inMemoryRepository(storedToken),
                helper
        );

        String tokenWithoutSubject = helper.generateAccessToken(null, Map.of("role", "ADMIN"));

        RestApiException exception = assertThrows(
                RestApiException.class,
                () -> service.validateAccessToken(tokenWithoutSubject)
        );
        assertEquals(JwtErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    @Test
    void validateAccessToken_subject_가_빈_문자열이면_INVALID_TOKEN_을_던진다() {
        AtomicReference<RefreshToken> storedToken = new AtomicReference<>();
        JwtTokenHelper helper = new JwtTokenHelper(SECRET, "swtp", 30, 7);
        JwtTokenManagementService service = new JwtTokenManagementService(
                inMemoryRepository(storedToken),
                helper
        );

        String tokenWithBlankSubject = helper.generateAccessToken("   ", Map.of("role", "ADMIN"));

        RestApiException exception = assertThrows(
                RestApiException.class,
                () -> service.validateAccessToken(tokenWithBlankSubject)
        );
        assertEquals(JwtErrorCode.INVALID_TOKEN, exception.getErrorCode());
    }

    private RefreshTokenRepository inMemoryRepository(AtomicReference<RefreshToken> storedToken) {
        RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
        when(repository.findByUserId(any())).thenAnswer(invocation -> {
            String userId = invocation.getArgument(0, String.class);
            RefreshToken token = storedToken.get();
            if (token == null || !token.getUserId().equals(userId)) {
                return Optional.empty();
            }
            return Optional.of(token);
        });
        when(repository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken token = invocation.getArgument(0, RefreshToken.class);
            storedToken.set(token);
            return token;
        });
        return repository;
    }
}
