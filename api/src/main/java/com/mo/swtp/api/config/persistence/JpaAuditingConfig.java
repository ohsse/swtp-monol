package com.mo.swtp.api.config.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화 설정.
 *
 * <p>{@code @SpringBootApplication} 과 분리하여 별도 {@code @Configuration} 으로 선언함으로써
 * {@code @DataJpaTest} 슬라이스 테스트에서 이 설정을 선택적으로 임포트할 수 있다.</p>
 *
 * <p>Auditor 소스는 {@link ApiAuditorAware} Bean(이름: {@code apiAuditorAware}) 을 참조한다.</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "apiAuditorAware")
public class JpaAuditingConfig {
}
