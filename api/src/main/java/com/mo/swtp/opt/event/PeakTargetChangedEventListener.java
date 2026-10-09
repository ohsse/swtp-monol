package com.mo.swtp.opt.event;

import com.mo.swtp.opt.sse.PeakTargetSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 전력피크 목표값 변경 이벤트 리스너 — SSE 전파 위임.
 *
 * <p>{@link TransactionalEventListener} {@code AFTER_COMMIT} 단계로 발화 — 변경 트랜잭션 commit
 * 이후에만 SSE send 가 발생한다 (롤백 시 send 미발생, PLAN1 §저장 흐름).</p>
 *
 * <p>{@code fallbackExecution = false} — 트랜잭션 없는 컨텍스트에서 발행된 이벤트는 무시한다.
 * 부주의한 호출 차단 안전망 (예: 테스트의 트랜잭션 외 publish).</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PeakTargetChangedEventListener {

    private final PeakTargetSseService sseService;

    /**
     * 변경 이벤트를 SSE 보관소에 broadcast 한다.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false
    )
    public void handle(PeakTargetChangedEvent event) {
        log.debug("전력피크 목표값 변경 SSE 전파 — targetPeakElpwr={}", event.targetPeakElpwr());
        sseService.broadcast(event);
    }
}
