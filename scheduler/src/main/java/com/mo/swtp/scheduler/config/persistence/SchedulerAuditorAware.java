package com.mo.swtp.scheduler.config.persistence;

import java.util.Optional;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Component;

/**
 * scheduler 모듈 전용 Auditor 제공자.
 *
 * <p>scheduler 는 HTTP 요청 컨텍스트가 없으므로 모든 쓰기 작업의 auditor 를
 * {@code "SYSTEM"} 으로 고정 반환한다.</p>
 */
@Component("schedulerAuditorAware")
public class SchedulerAuditorAware implements AuditorAware<String> {

    /**
     * {@code "SYSTEM"} 을 auditor 로 반환한다.
     */
    @Override
    public Optional<String> getCurrentAuditor() {
        return Optional.of("SYSTEM");
    }
}
