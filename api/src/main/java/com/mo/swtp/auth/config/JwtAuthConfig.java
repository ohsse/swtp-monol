package com.mo.swtp.auth.config;

import com.mo.swtp.common.jwt.JwtTokenHelper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(AuthJwtProperties.class)
public class JwtAuthConfig {

    /**
     * JWT 토큰 발급 및 검증 유틸리티를 등록한다.
     *
     * @param authJwtProperties JWT 인증 설정 정보
     * @return JWT 토큰 헬퍼
     */
    @Bean
    JwtTokenHelper jwtTokenHelper(AuthJwtProperties authJwtProperties) {
        return new JwtTokenHelper(
                authJwtProperties.getSecret(),
                authJwtProperties.getIssuer(),
                authJwtProperties.getAccessTokenExpirationMinutes(),
                authJwtProperties.getRefreshTokenExpirationDays()
        );
    }
}
