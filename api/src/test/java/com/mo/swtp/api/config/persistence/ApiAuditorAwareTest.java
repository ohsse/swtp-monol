package com.mo.swtp.api.config.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.mo.swtp.auth.web.JwtAuthenticationFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * {@link ApiAuditorAware} 단위 테스트.
 *
 * <p>케이스 1: request attribute 에 유효한 subject 가 있으면 해당 값을 반환한다.
 * 케이스 2: request attribute 에 subject 가 null/blank 이면 "SYSTEM" 을 반환한다.
 * 케이스 3: 요청 컨텍스트 자체가 없으면 "SYSTEM" 을 반환한다.</p>
 */
class ApiAuditorAwareTest {

    private final ApiAuditorAware auditorAware = new ApiAuditorAware();

    @AfterEach
    void clearContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void subject가_있으면_userId를_반환한다() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)).thenReturn("user-001");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains("user-001");
    }

    @Test
    void subject가_blank이면_SYSTEM을_반환한다() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)).thenReturn("  ");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains("SYSTEM");
    }

    @Test
    void subject가_null이면_SYSTEM을_반환한다() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE)).thenReturn(null);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains("SYSTEM");
    }

    @Test
    void 요청_컨텍스트가_없으면_SYSTEM을_반환한다() {
        Optional<String> auditor = auditorAware.getCurrentAuditor();

        assertThat(auditor).contains("SYSTEM");
    }
}
