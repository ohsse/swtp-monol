package com.mo.swtp.proc.event;

import com.mo.swtp.proc.sse.AiDrvnModeSseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * AI 운전모드 변경 이벤트 리스너 — SSE 전파 위임.
 *
 * <p>{@link TransactionalEventListener} {@code AFTER_COMMIT} 단계로 발화 — 변경 트랜잭션 commit
 * 이후에만 SSE send 가 발생한다 (롤백 시 send 미발생, PLAN2 §변경 트랜잭션 흐름 6단계).</p>
 *
 * <p>{@code fallbackExecution = false} — 트랜잭션 없는 컨텍스트에서 발행된 이벤트는 무시한다.
 * 부주의한 호출 차단 안전망 (예: 테스트의 트랜잭션 외 publish).</p>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AiDrvnModeChangedEventListener {

    private final AiDrvnModeSseService sseService;

    /**
     * 변경 이벤트를 SSE 보관소에 broadcast 한다.
     */
    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT,
            fallbackExecution = false
    )
    public void handle(AiDrvnModeChangedEvent event) {
        log.debug("AI 운전모드 변경 SSE 전파 — procId={}, mode={}", event.procId(), event.aiDrvnModCd());
        sseService.broadcast(event);
    }
}
