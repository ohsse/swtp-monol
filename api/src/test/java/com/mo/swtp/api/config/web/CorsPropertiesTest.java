package com.mo.swtp.api.config.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/**
 * {@link CorsProperties} binding 회귀 방지 테스트.
 *
 * <p>application*.yml 의 {@code cors.allowed-origins} 표기를 YAML list (`- ${VAR:a,b,c}`) 로 두면
 * placeholder default 가 list 의 1개 원소 (콤마 포함 단일 문자열) 로 들어가 매칭 불가 상태가 되었다.
 * 이를 단일 placeholder 표기로 정규화한 후, Spring Boot ConfigurationProperties 의 콤마 split 동작이
 * 의도대로 List 로 변환됨을 보증한다.</p>
 */
class CorsPropertiesTest {

    @Test
    void allowedOrigins_콤마_구분_문자열이_3원소_List_로_split_된다() {
        Map<String, Object> source = Map.of(
                "cors.allowed-origins", "http://a:1,http://b:2,http://c:3"
        );

        CorsProperties bound = bind(source);

        assertThat(bound.getAllowedOrigins())
                .containsExactly("http://a:1", "http://b:2", "http://c:3");
    }

    @Test
    void allowedOrigins_단일_origin_은_1원소_List_로_바인딩된다() {
        Map<String, Object> source = Map.of(
                "cors.allowed-origins", "https://swtp.gs.go.kr"
        );

        CorsProperties bound = bind(source);

        assertThat(bound.getAllowedOrigins())
                .containsExactly("https://swtp.gs.go.kr");
    }

    @Test
    void allowedOrigins_미설정_시_빈_리스트_default_가_유지된다() {
        Map<String, Object> source = Map.of();

        CorsProperties bound = bind(source);

        assertThat(bound.getAllowedOrigins()).isEmpty();
    }

    private CorsProperties bind(Map<String, Object> source) {
        Binder binder = new Binder(new MapConfigurationPropertySource(source));
        return binder.bindOrCreate("cors", CorsProperties.class);
    }
}
