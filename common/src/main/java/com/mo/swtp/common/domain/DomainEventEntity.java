package com.mo.swtp.common.domain;

import com.mo.swtp.common.event.DomainEventHolder;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Transient;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 도메인 이벤트 기능을 갖춘 엔티티 베이스 클래스.
 *
 * <p>{@link BaseEntity}를 확장하며, {@link DomainEventHolder}를 구현하여
 * 엔티티 생명주기 동안 발생하는 도메인 이벤트를 트랜잭션 내에 임시로 축적한다.
 * 이벤트는 DB에 저장되지 않으며({@code @Transient}), Publisher가 명시적으로 발행한 뒤 클리어한다.</p>
 *
 * <p>사용법:</p>
 * <pre>{@code
 * public class Pump extends DomainEventEntity {
 *     // 상태 변경 메서드 내부에서 이벤트 등록
 *     public void delete() {
 *         // ... 삭제 로직
 *     }
 * }
 *
 * // EventPublisher에서 발행
 * pumpEventPublisher.deleteAndPublish(pump, new PumpDeletedEvent(pump.getPumpId()));
 * }</pre>
 *
 * <p>Lombok {@code @NoArgsConstructor}를 사용하는 하위 엔티티는 정상 동작한다.
 * {@code domainEvents} 필드는 인스턴스 초기화 블록으로 설정되므로
 * 하위 클래스의 기본 생성자에서 {@code super()}가 자동으로 처리된다.</p>
 */
@MappedSuperclass
public abstract class DomainEventEntity extends BaseEntity implements DomainEventHolder {

    /** 트랜잭션 내 임시 이벤트 저장소. DB에 저장되지 않는다. */
    @Transient
    private final List<Object> domainEvents = new ArrayList<>();

    /**
     * {@inheritDoc}
     */
    @Override
    public void registerEvent(Object event) {
        this.domainEvents.add(event);
    }

    /**
     * {@inheritDoc}
     *
     * @return 수정 불가능한 이벤트 리스트 뷰
     */
    @Override
    public List<Object> getDomainEvents() {
        return Collections.unmodifiableList(this.domainEvents);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void clearDomainEvents() {
        this.domainEvents.clear();
    }
}
