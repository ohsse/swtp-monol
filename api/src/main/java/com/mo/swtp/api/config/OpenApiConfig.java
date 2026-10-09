package com.mo.swtp.api.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SpringDoc OpenAPI 설정.
 *
 * <p>본 Bean 은 다음 세 가지를 단일 SSOT 로 노출한다:
 * <ul>
 *   <li>API 메타정보 (title · description · version · contact · license)</li>
 *   <li>servers 목록 — {@link SwaggerProperties} 외부 설정 기반 tenant·환경별 분기</li>
 *   <li>JWT Bearer 인증 스키마 ({@code bearerAuth}) + 글로벌 SecurityRequirement —
 *       Swagger UI 의 "Authorize" 버튼이 노출되며 모든 endpoint 의 try-it-out 시 Authorization 헤더가 자동 첨부된다.
 *       인증 제외 경로(/api/auth/login 등) 는 backend 의 JWT 필터 exclude-paths 가 통과 처리하므로 swagger UI 에서 헤더가 보내져도 무관.</li>
 * </ul>
 *
 * <p>이전의 {@code @OpenAPIDefinition} 어노테이션 방식은 servers·security 의 외부 설정 주입이 불가하여
 * 본 Bean 코드 방식으로 일원화했다.</p>
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(SwaggerProperties.class)
public class OpenApiConfig {

    private static final String JWT_SCHEME_NAME = "bearerAuth";

    private final SwaggerProperties swaggerProperties;

    /**
     * SpringDoc 가 사용하는 OpenAPI Bean 을 단일 정의한다.
     *
     * @return OpenAPI 모델 — info · servers · components(securitySchemes) · security
     */
    @Bean
    public OpenAPI openAPI() {
        OpenAPI openAPI = new OpenAPI()
                .info(new Info()
                        .title("Smart WTP API")
                        .description("swtp API Swagger specification")
                        .version("v1")
                        .contact(new Contact().name("swtp backend"))
                        .license(new License().name("Proprietary")))
                .components(new Components()
                        .addSecuritySchemes(JWT_SCHEME_NAME, jwtSecurityScheme()))
                .addSecurityItem(new SecurityRequirement().addList(JWT_SCHEME_NAME));

        if (!swaggerProperties.getServers().isEmpty()) {
            List<Server> servers = swaggerProperties.getServers().stream()
                    .map(spec -> new Server().url(spec.getUrl()).description(spec.getDescription()))
                    .toList();
            openAPI.servers(servers);
        }

        return openAPI;
    }

    /**
     * JWT Bearer 인증 스키마 정의.
     *
     * @return HTTP Bearer + JWT bearerFormat
     */
    private SecurityScheme jwtSecurityScheme() {
        return new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("JWT Access Token (예: eyJhbGciOi... — Bearer 접두사는 자동 부여)");
    }
}
