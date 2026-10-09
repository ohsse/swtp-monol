package com.mo.swtp.auth.guard;

import com.mo.swtp.auth.exception.AuthErrorCode;
import com.mo.swtp.auth.web.JwtAuthenticationFilter;
import com.mo.swtp.common.exception.RestApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 컨트롤러 레이어에서 역할(Role) 기반 접근을 검사하는 컴포넌트.
 *
 * <p>{@link JwtAuthenticationFilter}가 request attribute에 적재한 claims를 사용한다.</p>
 */
@Component
public class RoleGuard {

    /**
     * ADMIN 역할 여부를 검사한다.
     *
     * <p>요청 attribute에 subject 또는 claims가 없거나 role이 ADMIN이 아닌 경우
     * FORBIDDEN 예외를 던진다. exclude-paths 설정 오류 등으로 필터가 건너뛰어져
     * 인증 컨텍스트가 누락된 경우도 차단한다.</p>
     *
     * @param request 현재 HTTP 요청 (subject·claims가 attribute로 적재되어 있어야 함)
     * @throws RestApiException FORBIDDEN — 인증 정보가 없거나 ADMIN 역할이 아닌 경우
     */
    @SuppressWarnings("unchecked")
    public void requireAdmin(HttpServletRequest request) {
        String subject = (String) request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE);
        Map<String, Object> claims =
                (Map<String, Object>) request.getAttribute(JwtAuthenticationFilter.AUTH_CLAIMS_ATTRIBUTE);

        if (subject == null || claims == null || !"ADMIN".equals(claims.get("role"))) {
            throw new RestApiException(AuthErrorCode.FORBIDDEN);
        }
    }
}
