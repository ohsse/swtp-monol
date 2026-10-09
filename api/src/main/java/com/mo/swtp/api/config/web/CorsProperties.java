package com.mo.swtp.api.config.web;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS 정책 외부 설정을 바인딩하는 프로퍼티 클래스이다.
 */
@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties(prefix = "cors")
public class CorsProperties {

    /**
     * 허용할 origin 목록이다. (예: http://localhost:5173)
     */
    private List<String> allowedOrigins = List.of();

    /**
     * 허용할 HTTP 메서드 목록이다.
     */
    private List<String> allowedMethods = List.of();

    /**
     * 허용할 요청 헤더 목록이다. ("*" 인 경우 전체 허용)
     */
    private List<String> allowedHeaders = List.of();

    /**
     * 자격 증명(쿠키·Authorization 헤더 등) 포함 요청 허용 여부이다.
     * JWT Bearer 토큰 헤더 기반이며 쿠키 미사용이므로 false 가 기본이다.
     */
    private boolean allowCredentials;

    /**
     * preflight 응답 캐싱 시간(초)이다.
     */
    private long maxAge;
}
