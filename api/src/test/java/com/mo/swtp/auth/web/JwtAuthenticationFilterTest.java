package com.mo.swtp.auth.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import tools.jackson.databind.ObjectMapper;
import com.mo.swtp.auth.config.AuthJwtProperties;
import com.mo.swtp.auth.service.JwtTokenManagementService;
import com.mo.swtp.common.exception.JwtErrorCode;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.common.jwt.JwtTokenInspection;
import com.mo.swtp.common.jwt.JwtTokenStatus;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterTest {

    @Test
    void skipsExcludedPath() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                mock(JwtTokenManagementService.class),
                new ApiErrorResponseWriter(new ObjectMapper()),
                createAuthJwtProperties()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/swagger-ui/index.html");
        request.setServletPath("/swagger-ui/index.html");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
    }

    @Test
    void nginx_프록시_경유_swagger_요청도_제외_경로로_매칭된다() throws Exception {
        // nginx 가 /api 프리픽스를 떼지 않고 backend 에 전달하는 환경(예: dev 30080 → 30081) 검증.
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                mock(JwtTokenManagementService.class),
                new ApiErrorResponseWriter(new ObjectMapper()),
                createAuthJwtProperties()
        );

        String[] proxyPaths = {
                "/api/swagger-ui.html",
                "/api/swagger-ui/index.html",
                "/api/v3/api-docs"
        };

        for (String path : proxyPaths) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
            request.setServletPath(path);
            MockHttpServletResponse response = new MockHttpServletResponse();
            MockFilterChain filterChain = new MockFilterChain();

            filter.doFilter(request, response, filterChain);

            assertEquals(200, response.getStatus(), "제외 경로여야 함: " + path);
        }
    }

    @Test
    void returnsUnauthorizedWhenAuthorizationHeaderIsMissing() throws Exception {
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                mock(JwtTokenManagementService.class),
                new ApiErrorResponseWriter(new ObjectMapper()),
                createAuthJwtProperties()
        );

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/resource");
        request.setServletPath("/secure/resource");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertEquals("{\"code\":\"MISSING_ACCESS_TOKEN\",\"data\":null}", response.getContentAsString());
    }

    @Test
    void setsRequestAttributesWhenTokenIsValid() throws Exception {
        JwtTokenManagementService service = mock(JwtTokenManagementService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                service,
                new ApiErrorResponseWriter(new ObjectMapper()),
                createAuthJwtProperties()
        );

        when(service.validateAccessToken("valid-token")).thenReturn(new JwtTokenInspection(
                JwtTokenStatus.VALID,
                "user-1",
                Map.of("role", "ADMIN"),
                Instant.now(),
                Instant.now().plusSeconds(60)
        ));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/resource");
        request.setServletPath("/secure/resource");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain filterChain = new MockFilterChain();

        filter.doFilter(request, response, filterChain);

        assertEquals("user-1", request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE));
        assertEquals(Map.of("role", "ADMIN"), request.getAttribute(JwtAuthenticationFilter.AUTH_CLAIMS_ATTRIBUTE));
        verify(service).validateAccessToken("valid-token");
    }

    @Test
    void returnsUnauthorizedWhenTokenValidationFails() throws Exception {
        JwtTokenManagementService service = mock(JwtTokenManagementService.class);
        JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
                service,
                new ApiErrorResponseWriter(new ObjectMapper()),
                createAuthJwtProperties()
        );

        doThrow(new RestApiException(JwtErrorCode.INVALID_TOKEN))
                .when(service)
                .validateAccessToken("invalid-token");

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/secure/resource");
        request.setServletPath("/secure/resource");
        request.addHeader("Authorization", "Bearer invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
        assertEquals("{\"code\":\"INVALID_TOKEN\",\"data\":null}", response.getContentAsString());
        assertNull(request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE));
    }

    private AuthJwtProperties createAuthJwtProperties() {
        AuthJwtProperties properties = new AuthJwtProperties();
        properties.setSecret("local-dev-only-secret-change-me-0123456789");
        properties.setIssuer("swtp-api");
        properties.setAccessTokenExpirationMinutes(30);
        properties.setRefreshTokenExpirationDays(7);
        properties.setExcludePaths(java.util.List.of(
                "/swagger-ui/**",
                "/v3/api-docs/**",
                "/swagger-ui.html",
                "/api/swagger-ui/**",
                "/api/v3/api-docs/**",
                "/api/swagger-ui.html",
                "/error"
        ));
        return properties;
    }
}
