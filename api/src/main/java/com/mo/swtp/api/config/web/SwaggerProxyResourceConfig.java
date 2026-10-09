package com.mo.swtp.api.config.web;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * nginx 리버스 프록시 환경(예: dev 30080 → backend 30081) 에서 /api 프리픽스가 그대로 전달될 때
 * swagger-ui webjar 정적 자원이 /api/swagger-ui/** 경로로 매핑되도록 보조한다.
 *
 * <p>SpringDoc 의 {@code springdoc.swagger-ui.path} 설정은 swagger-ui HTML 진입점만 옮기고
 * webjar 정적 자원(swagger-ui-bundle.js · swagger-ui.css 등) 의 ResourceHandler 등록 경로
 * (/swagger-ui/**) 는 변경하지 않는다. 따라서 nginx 가 prefix 를 떼지 않고 전달하는 시나리오에서는
 * 본 ResourceHandler 보조 매핑이 없으면 ResourceHttpRequestHandler 가 자원을 찾지 못해 404 가 발생한다.
 *
 * <p>swagger-ui 활성 여부에 종속하여 등록한다 — 운영 tenant(gs 등) 의 swagger 비활성 환경에서
 * 정적 자원이 무인증 노출되지 않도록 함이다 (multi-tenant.md §3·§5 의 tenant 별 SpringDoc 분기 정책).
 */
@Configuration
@ConditionalOnProperty(prefix = "springdoc.swagger-ui", name = "enabled", havingValue = "true")
public class SwaggerProxyResourceConfig implements WebMvcConfigurer {

    /**
     * /api/swagger-ui/** 요청을 swagger-ui webjar 정적 자원 디렉토리로 매핑한다.
     * webjars-locator-core (springdoc-openapi-starter-webmvc-ui 의 transitive 의존) 가
     * classpath 에 존재하므로 버전 디렉토리는 자동 해석된다.
     *
     * @param registry Spring MVC 의 ResourceHandler 레지스트리
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/api/swagger-ui/**")
                .addResourceLocations("classpath:/META-INF/resources/webjars/swagger-ui/");
    }
}
