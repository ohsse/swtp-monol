package com.mo.swtp.auth.guard;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mo.swtp.auth.exception.AuthErrorCode;
import com.mo.swtp.auth.web.JwtAuthenticationFilter;
import com.mo.swtp.common.exception.RestApiException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * {@link RoleGuard} 단위 테스트.
 */
class RoleGuardTest {

    private final RoleGuard roleGuard = new RoleGuard();

    @Test
    void ADMIN_역할이면_예외없이_통과한다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE, "admin-001");
        request.setAttribute(JwtAuthenticationFilter.AUTH_CLAIMS_ATTRIBUTE, Map.of("role", "ADMIN"));

        assertThatCode(() -> roleGuard.requireAdmin(request)).doesNotThrowAnyException();
    }

    @Test
    void ADMIN_이_아닌_역할이면_FORBIDDEN을_던진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE, "user-001");
        request.setAttribute(JwtAuthenticationFilter.AUTH_CLAIMS_ATTRIBUTE, Map.of("role", "USER"));

        assertThatThrownBy(() -> roleGuard.requireAdmin(request))
                .isInstanceOf(RestApiException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.FORBIDDEN);
    }

    @Test
    void claims가_없으면_FORBIDDEN을_던진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE, "admin-001");

        assertThatThrownBy(() -> roleGuard.requireAdmin(request))
                .isInstanceOf(RestApiException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.FORBIDDEN);
    }

    @Test
    void subject가_없으면_FORBIDDEN을_던진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTH_CLAIMS_ATTRIBUTE, Map.of("role", "ADMIN"));

        assertThatThrownBy(() -> roleGuard.requireAdmin(request))
                .isInstanceOf(RestApiException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.FORBIDDEN);
    }

    @Test
    void role_클레임이_없으면_FORBIDDEN을_던진다() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE, "admin-001");
        request.setAttribute(JwtAuthenticationFilter.AUTH_CLAIMS_ATTRIBUTE, Map.of());

        assertThatThrownBy(() -> roleGuard.requireAdmin(request))
                .isInstanceOf(RestApiException.class)
                .extracting("errorCode")
                .isEqualTo(AuthErrorCode.FORBIDDEN);
    }
}
