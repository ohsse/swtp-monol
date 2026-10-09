package com.mo.swtp.scheduler.config.persistence;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * scheduler 모듈 JPA Auditing 활성화 설정.
 *
 * <p>Auditor 소스는 {@link SchedulerAuditorAware} Bean(이름: {@code schedulerAuditorAware}) 을 참조한다.
 * scheduler 는 HTTP 요청 컨텍스트가 없으므로 auditor 는 {@code "SYSTEM"} 고정값을 반환한다.</p>
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "schedulerAuditorAware")
public class JpaAuditingConfig {
}
