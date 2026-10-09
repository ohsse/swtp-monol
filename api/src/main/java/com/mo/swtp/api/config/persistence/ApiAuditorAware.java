package com.mo.swtp.api.config.persistence;

import com.mo.swtp.auth.web.JwtAuthenticationFilter;
import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * JWT 인증 필터가 적재한 request attribute 에서 현재 사용자 ID 를 추출하는 Auditor 제공자.
 *
 * <p>{@link JwtAuthenticationFilter} 가 이미 서명 검증을 완료하고
 * {@link JwtAuthenticationFilter#AUTH_SUBJECT_ATTRIBUTE} request attribute 에 subject(userId) 를 세팅한다.
 * 이 Bean 은 해당 attribute 를 읽어 Auditing 컬럼({@code rgstr_id}/{@code updt_id}) 에 주입한다.</p>
 *
 * <p>헤더를 직접 재파싱하거나 서명 검증 없이 JWT 를 decode 하지 않는다.</p>
 *
 * <p>요청 컨텍스트가 없거나(로그인 API 내부·비HTTP 경로) subject 가 비어 있는 경우
 * {@code "SYSTEM"} 을 auditor 로 반환한다.</p>
 */
@Component("apiAuditorAware")
public class ApiAuditorAware implements AuditorAware<String> {

    private static final String SYSTEM_AUDITOR = "SYSTEM";

    /**
     * 현재 요청의 subject(userId) 를 반환한다. 컨텍스트 부재·비인증 시 {@code "SYSTEM"} 반환.
     */
    @Override
    public Optional<String> getCurrentAuditor() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (!(attrs instanceof ServletRequestAttributes sra)) {
            return Optional.of(SYSTEM_AUDITOR);
        }
        Object subject = sra.getRequest().getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE);
        if (subject instanceof String s && !s.isBlank()) {
            return Optional.of(s);
        }
        return Optional.of(SYSTEM_AUDITOR);
    }
}
