package com.mo.swtp.api.config;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Swagger UI 노출 메타데이터의 외부 설정.
 *
 * <p>OpenAPI spec 의 {@code servers} 항목과 같이 tenant·환경마다 달라지는 값을
 * application.yml({@code swagger.servers}) 로 외부화한다.
 * application-common.yml 에 default 가 없으므로 미설정 tenant 는 빈 리스트로 바인딩되며
 * 이 경우 SpringDoc 가 요청 URL 기반 default server 를 자동 사용한다.</p>
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "swagger")
public class SwaggerProperties {

    /**
     * Swagger UI 의 servers 드롭다운에 노출할 base URL 목록.
     * URL 에는 컨트롤러 매핑의 prefix(/api 등) 를 포함하지 않는다 — OpenAPI spec 의 path 가
     * 이미 해당 prefix 를 포함하므로 server.url 에 또 넣으면 이중 prefix 가 발생한다.
     */
    private List<ServerSpec> servers = new ArrayList<>();

    @Getter
    @Setter
    public static class ServerSpec {
        /** base URL — 예: {@code http://localhost:30080}. */
        private String url;
        /** Swagger UI 드롭다운에 표시할 설명 — 예: {@code dev (nginx 경유 30080)}. */
        private String description;
    }
}
