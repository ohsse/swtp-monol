package com.mo.swtp.common.event;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.function.Function;

/**
 * 도메인 이벤트 발행 추상 클래스.
 *
 * <p>엔티티의 저장/삭제와 도메인 이벤트 발행을 하나의 흐름으로 캡슐화한다.
 * 구체 Publisher는 이 클래스를 상속하여 도메인별 비즈니스 메서드를 제공한다.</p>
 *
 * <p>이벤트 발행 시점:</p>
 * <ul>
 *   <li>{@code saveAndPublish} — 엔티티 저장 직후 이벤트 발행</li>
 *   <li>{@code deleteAndPublish} — 이벤트 등록 후 엔티티 삭제, 이후 발행</li>
 *   <li>{@code publishAndClear} — 저장/삭제 없이 축적된 이벤트만 발행</li>
 * </ul>
 *
 * <p>사용 예시:</p>
 * <pre>{@code
 * @Component
 * @RequiredArgsConstructor
 * public class PumpEventPublisher extends AbstractDomainEventPublisher<Pump> {
 *
 *     private final PumpRepository pumpRepository;
 *     private final ApplicationEventPublisher publisher;
 *
 *     public PumpEventPublisher(PumpRepository pumpRepository,
 *                               ApplicationEventPublisher publisher) {
 *         super(pumpRepository, publisher);
 *     }
 *
 *     public Pump saveAndPublish(Pump pump) {
 *         return saveAndPublish(pump, saved -> new PumpCreatedEvent(saved.getPumpId()));
 *     }
 *
 *     public void deleteAndPublish(Pump pump) {
 *         deleteAndPublish(pump, new PumpDeletedEvent(pump.getPumpId()));
 *     }
 * }
 * }</pre>
 *
 * @param <T> {@link DomainEventHolder}를 구현하는 엔티티 타입
 */
public abstract class AbstractDomainEventPublisher<T extends DomainEventHolder> {

    /** 엔티티 영속화에 사용되는 JPA 리포지토리 */
    protected final JpaRepository<T, ?> repository;

    /** Spring 이벤트 발행기 */
    protected final ApplicationEventPublisher publisher;

    /**
     * 생성자.
     *
     * @param repository 엔티티 리포지토리
     * @param publisher  Spring 이벤트 발행기
     */
    protected AbstractDomainEventPublisher(JpaRepository<T, ?> repository,
                                           ApplicationEventPublisher publisher) {
        this.repository = repository;
        this.publisher = publisher;
    }

    /**
     * 엔티티를 저장하고, 이벤트를 생성하여 발행한다.
     *
     * <p>{@code eventFunction}이 {@code null}이 아니면 저장된 엔티티를 인자로 이벤트를 생성하여 등록한다.
     * {@code null}이면 새 이벤트를 등록하지 않는다.</p>
     *
     * <p>저장 완료 후 항상 {@link #publishAndClear(DomainEventHolder)}가 호출된다.
     * 따라서 {@code eventFunction}이 {@code null}이더라도 엔티티에 이미 등록된 이벤트가 있다면 모두 발행된다.</p>
     *
     * @param entity        저장할 엔티티
     * @param eventFunction 저장된 엔티티를 받아 이벤트 객체를 반환하는 함수 (null 허용)
     * @return 저장된 엔티티
     */
    protected T saveAndPublish(T entity, Function<T, Object> eventFunction) {
        T savedEntity = repository.save(entity);

        if (eventFunction != null) {
            Object event = eventFunction.apply(savedEntity);
            savedEntity.registerEvent(event);
        }

        this.publishAndClear(savedEntity);
        return savedEntity;
    }

    /**
     * 이벤트를 등록한 뒤 엔티티를 JPA 영속성 컨텍스트에서 삭제 마킹하고, 이벤트를 발행한다.
     *
     * <p>실행 순서:</p>
     * <ol>
     *   <li>이벤트를 엔티티에 등록한다.</li>
     *   <li>{@code repository.delete(entity)}로 삭제를 영속성 컨텍스트에 예약한다 (SQL 미실행).</li>
     *   <li>{@link #publishAndClear(DomainEventHolder)}로 이벤트를 Spring에 발행한다.</li>
     * </ol>
     *
     * <p>{@code @TransactionalEventListener(BEFORE_COMMIT)} 핸들러는 트랜잭션 커밋 직전에 실행되므로,
     * 연관 데이터 정리 SQL이 메인 엔티티 DELETE SQL 이전에 flush된다.
     * 단, Hibernate flush 순서는 JPA 구현체에 따라 달라질 수 있으므로
     * FK 제약이 있는 경우 핸들러 내에서 {@code EntityManager.flush()} 호출을 권장한다.</p>
     *
     * @param entity 삭제할 엔티티
     * @param event  발행할 도메인 이벤트
     */
    protected void deleteAndPublish(T entity, Object event) {
        entity.registerEvent(event);
        repository.delete(entity);
        this.publishAndClear(entity);
    }

    /**
     * 엔티티에 축적된 이벤트를 모두 발행하고 클리어한다.
     *
     * @param entity 이벤트를 보유한 엔티티
     */
    protected void publishAndClear(T entity) {
        entity.getDomainEvents().forEach(publisher::publishEvent);
        entity.clearDomainEvents();
    }

    /**
     * 이벤트를 등록하고, 축적된 이벤트를 모두 발행한 뒤 클리어한다.
     *
     * @param entity 이벤트를 등록할 엔티티
     * @param event  발행할 도메인 이벤트
     */
    protected void publishAndClear(T entity, Object event) {
        entity.registerEvent(event);
        this.publishAndClear(entity);
    }
}
