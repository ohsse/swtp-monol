package com.mo.swtp.common.event;

import java.util.List;

/**
 * 도메인 이벤트 홀더 인터페이스.
 *
 * <p>도메인 이벤트를 생성하는 엔티티가 구현한다.
 * 이벤트는 트랜잭션 커밋 전/후 {@link org.springframework.context.ApplicationEventPublisher}를
 * 통해 발행되며, {@link org.springframework.transaction.event.TransactionalEventListener}로 처리된다.</p>
 *
 * <ul>
 *   <li>BEFORE_COMMIT — 동기, 같은 트랜잭션 내 연관 데이터 정리</li>
 *   <li>AFTER_COMMIT  — {@code @Async} 비동기, 파일 삭제·캐시 무효화 등 부수 효과</li>
 * </ul>
 */
public interface DomainEventHolder {

    /**
     * 이벤트를 등록한다.
     *
     * @param event 등록할 도메인 이벤트 객체
     */
    void registerEvent(Object event);

    /**
     * 등록된 이벤트 목록을 반환한다 (불변).
     *
     * @return 등록된 이벤트의 읽기 전용 리스트
     */
    List<Object> getDomainEvents();

    /**
     * 등록된 이벤트를 모두 제거한다.
     * 이벤트 발행 후 호출하여 중복 발행을 방지한다.
     */
    void clearDomainEvents();
}
