package com.mo.swtp.proc.event;

import com.mo.swtp.common.event.AbstractDomainEventPublisher;
import com.mo.swtp.proc.domain.AiDrvnMode;
import com.mo.swtp.proc.repository.AiDrvnModeRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * AI 운전모드 도메인 이벤트 발행기.
 *
 * <p>{@link AbstractDomainEventPublisher} 를 상속하여 {@link AiDrvnMode} 엔티티가
 * 자기 상태 변경 메서드 ({@link AiDrvnMode#change}/{@link AiDrvnMode#create}) 내부에서
 * {@code registerEvent()} 로 축적한 {@link AiDrvnModeChangedEvent} 를 트랜잭션 내
 * 마지막 단계에서 발행한다.</p>
 *
 * <p>도입: 송수펌프제어분석-2번섹션 PLAN3 + TASK2 Phase 3 (2026-05-20).
 * 옵션 B ({@link ApplicationEventPublisher} Service 직접 주입) 결정 폐기에 따른
 * 모듈 SSOT 패턴 ({@code DomainEventEntity} + 전용 Publisher) 정렬 — REVIEW1 블로커 해소.
 * 선례: {@code UserEventPublisher} ({@code com.mo.swtp.user.event}).</p>
 */
@Component
public class AiDrvnModeEventPublisher extends AbstractDomainEventPublisher<AiDrvnMode> {

    /**
     * 생성자.
     *
     * @param repository AI 운전모드 리포지토리
     * @param publisher  Spring 이벤트 발행기
     */
    public AiDrvnModeEventPublisher(AiDrvnModeRepository repository,
                                    ApplicationEventPublisher publisher) {
        super(repository, publisher);
    }

    /**
     * 기존 모드 변경 시 축적된 도메인 이벤트를 발행하고 클리어한다.
     *
     * <p>엔티티 상태는 JPA dirty checking 으로 flush 되므로 별도 {@code save()} 호출 불필요.
     * 트랜잭션 commit 직전에 본 메서드를 호출하여 AFTER_COMMIT Listener (SSE 전파) 가
     * 영속화 완료 후에만 발화하도록 한다.</p>
     *
     * @param mode 모드 변경된 AI 운전모드 엔티티
     */
    public void changeAndPublish(AiDrvnMode mode) {
        super.publishAndClear(mode);
    }

    /**
     * 신규 AI 운전모드 행을 저장하고 축적된 도메인 이벤트를 발행한다.
     *
     * <p>{@link AiDrvnMode#create} 가 이미 {@link AiDrvnModeChangedEvent} 1건을
     * {@code registerEvent()} 로 축적한 상태이므로 {@code eventFunction} 은 {@code null} —
     * 신규 등록 없이 축적된 이벤트만 발행한다
     * ({@link AbstractDomainEventPublisher#saveAndPublish} 동작).</p>
     *
     * @param mode 최초 설정된 AI 운전모드 엔티티
     * @return 저장된 엔티티
     */
    public AiDrvnMode createAndPublish(AiDrvnMode mode) {
        return super.saveAndPublish(mode, null);
    }
}
