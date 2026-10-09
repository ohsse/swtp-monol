package com.mo.swtp.auth.web;

import com.mo.swtp.auth.config.AuthJwtProperties;
import com.mo.swtp.auth.service.JwtTokenManagementService;
import com.mo.swtp.common.exception.JwtErrorCode;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.common.jwt.JwtTokenInspection;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_SUBJECT_ATTRIBUTE = "swtp.auth.subject";
    public static final String AUTH_CLAIMS_ATTRIBUTE = "swtp.auth.claims";

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenManagementService jwtTokenManagementService;
    private final ApiErrorResponseWriter apiErrorResponseWriter;
    private final List<String> excludedPathPatterns;
    private final AntPathMatcher antPathMatcher = new AntPathMatcher();

    /**
     * JWT 인증 필터를 생성한다.
     *
     * @param jwtTokenManagementService JWT 토큰 관리 서비스
     * @param apiErrorResponseWriter API 에러 응답 작성기
     * @param authJwtProperties JWT 인증 설정 정보
     */
    public JwtAuthenticationFilter(
            JwtTokenManagementService jwtTokenManagementService,
            ApiErrorResponseWriter apiErrorResponseWriter,
            AuthJwtProperties authJwtProperties
    ) {
        this.jwtTokenManagementService = jwtTokenManagementService;
        this.apiErrorResponseWriter = apiErrorResponseWriter;
        this.excludedPathPatterns = List.copyOf(authJwtProperties.getExcludePaths());
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String path = request.getServletPath();
        return excludedPathPatterns.stream().anyMatch(pattern -> antPathMatcher.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String accessToken = resolveBearerToken(request);
            JwtTokenInspection inspection = jwtTokenManagementService.validateAccessToken(accessToken);
            request.setAttribute(AUTH_SUBJECT_ATTRIBUTE, inspection.subject());
            request.setAttribute(AUTH_CLAIMS_ATTRIBUTE, inspection.claims());
            filterChain.doFilter(request, response);
        } catch (RestApiException exception) {
            apiErrorResponseWriter.write(response, exception.getErrorCode());
        }
    }

    private String resolveBearerToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new RestApiException(JwtErrorCode.MISSING_ACCESS_TOKEN);
        }
        if (!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new RestApiException(JwtErrorCode.INVALID_TOKEN);
        }
        String token = authorizationHeader.substring(BEARER_PREFIX.length()).trim();
        if (token.isEmpty()) {
            throw new RestApiException(JwtErrorCode.INVALID_TOKEN);
        }
        return token;
    }
}
