package com.mo.swtp.user.event;

import com.mo.swtp.auth.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 사용자 도메인 이벤트 핸들러.
 *
 * <p>모든 리스너는 {@code BEFORE_COMMIT} 단계에서 동일 트랜잭션 내에 실행되어
 * 연관 RefreshToken 정리와 사용자 삭제가 하나의 트랜잭션으로 커밋된다.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class UserEventHandler {

    private final RefreshTokenRepository refreshTokenRepository;

    /**
     * 사용자 비활성화 시 해당 사용자의 리프레시 토큰을 폐기한다.
     *
     * @param event 사용자 비활성화 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(UserDeactivatedEvent event) {
        refreshTokenRepository.findByUserId(event.userId())
                .ifPresent(token -> {
                    token.revoke(LocalDateTime.now());
                    log.debug("사용자 비활성화로 인한 리프레시 토큰 폐기 — userId={}", event.userId());
                });
    }

    /**
     * 사용자 물리 삭제 시 FK 제약 위반을 방지하기 위해 리프레시 토큰을 먼저 삭제한다.
     *
     * <p>{@code @Modifying @Query}를 사용한 JPQL 직접 DELETE로 SQL이 즉시 실행되므로,
     * Hibernate가 사용자 DELETE를 flush하기 전에 refresh_token_p 레코드가 제거된다.</p>
     *
     * @param event 사용자 삭제 이벤트
     */
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void handle(UserDeletedEvent event) {
        refreshTokenRepository.deleteByUserId(event.userId());
        log.debug("사용자 물리 삭제로 인한 리프레시 토큰 삭제 — userId={}", event.userId());
    }
}
