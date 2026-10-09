package com.mo.swtp.auth.config;

import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 인증 관련 외부 설정을 바인딩하는 프로퍼티 클래스이다.
 */
@Getter
@Setter
@NoArgsConstructor
@ConfigurationProperties(prefix = "auth.jwt")
public class AuthJwtProperties {

    /**
     * JWT 서명에 사용하는 비밀키이다.
     */
    private String secret;

    /**
     * JWT 발급자 정보이다.
     */
    private String issuer;

    /**
     * Access token 만료 시간(분)이다.
     */
    private long accessTokenExpirationMinutes;

    /**
     * Refresh token 만료 시간(일)이다.
     */
    private long refreshTokenExpirationDays;

    /**
     * JWT 인증 필터를 적용하지 않는 요청 경로 패턴 목록이다.
     */
    private List<String> excludePaths = List.of();
}
