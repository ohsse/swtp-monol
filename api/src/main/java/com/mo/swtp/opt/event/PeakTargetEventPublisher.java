package com.mo.swtp.opt.event;

import com.mo.swtp.common.event.AbstractDomainEventPublisher;
import com.mo.swtp.opt.domain.PeakTarget;
import com.mo.swtp.opt.repository.PeakTargetRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 전력피크 목표값 도메인 이벤트 발행기.
 *
 * <p>{@link AbstractDomainEventPublisher} 를 상속하여 {@link PeakTarget} 엔티티가 자기 상태 변경
 * 메서드 ({@link PeakTarget#change}) 내부에서 {@code registerEvent()} 로 축적한
 * {@link PeakTargetChangedEvent} 를 트랜잭션 내 마지막 단계에서 발행한다.</p>
 *
 * <p>도입: 전력피크분석-1번섹션 PLAN1 + TASK1 Phase 3 (2026-06-04).
 * 모듈 SSOT 패턴 ({@code DomainEventEntity} + 전용 Publisher) 정렬 — Service 에
 * {@link ApplicationEventPublisher} 직접 주입 금지. 선례: {@code AiDrvnModeEventPublisher}
 * ({@code com.mo.swtp.proc.event}).</p>
 */
@Component
public class PeakTargetEventPublisher extends AbstractDomainEventPublisher<PeakTarget> {

    /**
     * 생성자.
     *
     * @param repository 전력피크 목표값 리포지토리
     * @param publisher  Spring 이벤트 발행기
     */
    public PeakTargetEventPublisher(PeakTargetRepository repository,
                                    ApplicationEventPublisher publisher) {
        super(repository, publisher);
    }

    /**
     * 목표값 변경 시 축적된 도메인 이벤트를 발행하고 클리어한다.
     *
     * <p>엔티티 상태는 JPA dirty checking 으로 flush 되므로 별도 {@code save()} 호출 불필요.
     * 트랜잭션 commit 직전에 본 메서드를 호출하여 AFTER_COMMIT Listener (SSE 전파) 가
     * 영속화 완료 후에만 발화하도록 한다.</p>
     *
     * @param peakTarget 목표값이 변경된 전력피크 목표값 엔티티
     */
    public void changeAndPublish(PeakTarget peakTarget) {
        super.publishAndClear(peakTarget);
    }
}
