package com.mo.swtp.common.event;

import com.mo.swtp.common.domain.DomainEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.jpa.repository.JpaRepository;

import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link AbstractDomainEventPublisher} 단위 테스트.
 *
 * <p>Mock {@link JpaRepository}와 Mock {@link ApplicationEventPublisher}를 사용하여
 * 저장/삭제/발행 흐름을 검증한다. Spring 컨텍스트를 사용하지 않는다.</p>
 */
@ExtendWith(MockitoExtension.class)
class AbstractDomainEventPublisherTest {

    @Mock
    private JpaRepository<TestEntity, String> repository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    private TestPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new TestPublisher(repository, applicationEventPublisher);
    }

    @Test
    @DisplayName("saveAndPublish — 저장 후 이벤트가 발행되고 엔티티의 이벤트가 클리어된다")
    void saveAndPublish_saves_and_publishes_event() {
        TestEntity entity = new TestEntity();
        String testEvent = "created";
        when(repository.save(entity)).thenReturn(entity);

        publisher.saveAndPublish(entity, saved -> testEvent);

        verify(repository).save(entity);
        verify(applicationEventPublisher).publishEvent(testEvent);
        assertTrue(entity.getDomainEvents().isEmpty(), "발행 후 이벤트 목록이 비어야 한다");
    }

    @Test
    @DisplayName("saveAndPublish — eventFunction이 null이고 사전 등록 이벤트 없으면 발행하지 않는다")
    void saveAndPublish_with_null_function_and_no_prior_events_does_not_publish() {
        TestEntity entity = new TestEntity();
        when(repository.save(entity)).thenReturn(entity);

        publisher.saveAndPublish(entity, null);

        verify(repository).save(entity);
        verify(applicationEventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("saveAndPublish — eventFunction이 null이어도 사전 등록 이벤트는 발행된다")
    void saveAndPublish_with_null_function_publishes_pre_registered_events() {
        TestEntity entity = new TestEntity();
        String preEvent = "pre-registered";
        entity.registerEvent(preEvent);
        when(repository.save(entity)).thenReturn(entity);

        publisher.saveAndPublish(entity, null);

        verify(applicationEventPublisher).publishEvent(preEvent);
        assertTrue(entity.getDomainEvents().isEmpty(), "발행 후 이벤트 목록이 비어야 한다");
    }

    @Test
    @DisplayName("deleteAndPublish — repository.delete 후 이벤트가 발행되고 엔티티가 삭제된다")
    void deleteAndPublish_deletes_first_then_publishes_event() {
        TestEntity entity = new TestEntity();
        String testEvent = "deleted";

        publisher.deleteAndPublish(entity, testEvent);

        // delete → publishEvent 순서 검증
        InOrder inOrder = inOrder(repository, applicationEventPublisher);
        inOrder.verify(repository).delete(entity);
        inOrder.verify(applicationEventPublisher).publishEvent(testEvent);

        assertTrue(entity.getDomainEvents().isEmpty(), "발행 후 이벤트 목록이 비어야 한다");
    }

    @Test
    @DisplayName("publishAndClear(entity) — 축적된 이벤트가 모두 발행되고 클리어된다")
    void publishAndClear_publishes_all_registered_events() {
        TestEntity entity = new TestEntity();
        String event1 = "event-a";
        String event2 = "event-b";
        entity.registerEvent(event1);
        entity.registerEvent(event2);

        publisher.publishAndClear(entity);

        verify(applicationEventPublisher).publishEvent(event1);
        verify(applicationEventPublisher).publishEvent(event2);
        assertTrue(entity.getDomainEvents().isEmpty(), "발행 후 이벤트 목록이 비어야 한다");
    }

    @Test
    @DisplayName("publishAndClear(entity, event) — 이벤트를 등록 후 발행하고 클리어한다")
    void publishAndClear_with_event_registers_and_publishes() {
        TestEntity entity = new TestEntity();
        String testEvent = "new-event";

        publisher.publishAndClear(entity, testEvent);

        verify(applicationEventPublisher).publishEvent(testEvent);
        assertTrue(entity.getDomainEvents().isEmpty(), "발행 후 이벤트 목록이 비어야 한다");
    }

    // ----- 테스트 전용 내부 클래스 -----

    /** 테스트용 구체 엔티티 */
    static class TestEntity extends DomainEventEntity {
    }

    /** 테스트용 구체 Publisher — protected 메서드를 public으로 노출 */
    static class TestPublisher extends AbstractDomainEventPublisher<TestEntity> {

        TestPublisher(JpaRepository<TestEntity, String> repository,
                      ApplicationEventPublisher publisher) {
            super(repository, publisher);
        }

        @Override
        public TestEntity saveAndPublish(TestEntity entity,
                                         java.util.function.Function<TestEntity, Object> eventFunction) {
            return super.saveAndPublish(entity, eventFunction);
        }

        @Override
        public void deleteAndPublish(TestEntity entity, Object event) {
            super.deleteAndPublish(entity, event);
        }

        @Override
        public void publishAndClear(TestEntity entity) {
            super.publishAndClear(entity);
        }

        @Override
        public void publishAndClear(TestEntity entity, Object event) {
            super.publishAndClear(entity, event);
        }
    }
}
