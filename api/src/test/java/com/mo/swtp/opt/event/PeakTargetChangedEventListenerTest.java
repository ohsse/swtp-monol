package com.mo.swtp.opt.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.mo.swtp.auth.web.ApiErrorResponseWriter;
import com.mo.swtp.opt.sse.PeakTargetSseService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * {@link PeakTargetChangedEventListener} 통합 테스트 — AFTER_COMMIT 단계 발화 검증.
 *
 * <p>핵심 검증:</p>
 * <ul>
 *   <li>트랜잭션 commit 후에만 SseService.broadcast 호출</li>
 *   <li>트랜잭션 rollback 시 broadcast 미호출</li>
 *   <li>fallbackExecution=false — 트랜잭션 없는 publish 는 무시</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class PeakTargetChangedEventListenerTest {

    @MockitoBean
    private ApiErrorResponseWriter apiErrorResponseWriter;

    @MockitoBean
    private PeakTargetSseService sseService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Test
    void 트랜잭션_커밋_후에만_SseService_broadcast가_호출된다() {
        PeakTargetChangedEvent event = new PeakTargetChangedEvent(
                new BigDecimal("900.0000"), LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> eventPublisher.publishEvent(event));

        ArgumentCaptor<PeakTargetChangedEvent> captor = ArgumentCaptor.forClass(PeakTargetChangedEvent.class);
        then(sseService).should(times(1)).broadcast(captor.capture());
        assertThat(captor.getValue().targetPeakElpwr()).isEqualByComparingTo(new BigDecimal("900.0000"));
    }

    @Test
    void 트랜잭션_롤백_시_broadcast가_호출되지_않는다() {
        PeakTargetChangedEvent event = new PeakTargetChangedEvent(
                new BigDecimal("123.4500"), LocalDateTime.now());

        transactionTemplate.executeWithoutResult(status -> {
            eventPublisher.publishEvent(event);
            status.setRollbackOnly();
        });

        then(sseService).should(never()).broadcast(any(PeakTargetChangedEvent.class));
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void 트랜잭션_없이_publish하면_fallbackExecution_false로_무시된다() {
        PeakTargetChangedEvent event = new PeakTargetChangedEvent(
                new BigDecimal("777.0000"), LocalDateTime.now());

        eventPublisher.publishEvent(event);

        then(sseService).should(never()).broadcast(any(PeakTargetChangedEvent.class));
    }
}
