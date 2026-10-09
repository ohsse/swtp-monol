package com.mo.swtp.api.config.web;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * api 모듈의 Spring MVC 전역 설정을 등록한다.
 *
 * <p>현재는 CORS 정책 등록을 담당하며, 정책 값은 {@link CorsProperties}
 * (yml 외부 설정 + 환경변수 주입) 으로부터 바인딩한다.</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(CorsProperties.class)
public class WebMvcConfig implements WebMvcConfigurer {

    private final CorsProperties corsProperties;

    /**
     * 시작 시점에 binding 된 CORS 설정을 INFO 로 노출해 yml 형식 회귀를 신속히 진단할 수 있도록 한다.
     * (운영 도입 후 진단 노이즈가 누적되면 본 메서드 제거 가능 — 본 작업은 dev 환경 진단 목적.)
     */
    @PostConstruct
    public void logCorsConfig() {
        log.info("CORS allowed-origins (size={}): {}",
                corsProperties.getAllowedOrigins().size(), corsProperties.getAllowedOrigins());
        log.info("CORS allowed-methods: {}", corsProperties.getAllowedMethods());
        log.info("CORS allowed-headers: {}", corsProperties.getAllowedHeaders());
    }

    /**
     * 전역 CORS 매핑을 등록한다. 인증 필터({@code JwtAuthenticationFilter}) 가
     * preflight(OPTIONS) 요청을 우회하므로, CORS 응답 헤더는 본 매핑이 단독으로 책임진다.
     *
     * @param registry CORS 매핑 레지스트리
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(corsProperties.getAllowedOrigins().toArray(String[]::new))
                .allowedMethods(corsProperties.getAllowedMethods().toArray(String[]::new))
                .allowedHeaders(corsProperties.getAllowedHeaders().toArray(String[]::new))
                .allowCredentials(corsProperties.isAllowCredentials())
                .maxAge(corsProperties.getMaxAge());
    }
}
