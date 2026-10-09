package com.mo.swtp.common.event;

import com.mo.swtp.common.domain.DomainEventEntity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link DomainEventEntity} 이벤트 등록/조회/클리어 단위 테스트.
 *
 * <p>Spring 컨텍스트 없이 순수 JUnit5로 실행된다.</p>
 */
class DomainEventEntityTest {

    /** 테스트용 구체 엔티티 */
    private DomainEventEntity entity;

    @BeforeEach
    void setUp() {
        entity = new DomainEventEntity() {
            // 테스트 전용 익명 구현체
        };
    }

    @Test
    @DisplayName("registerEvent — 이벤트가 순서대로 축적된다")
    void registerEvent_accumulates_in_order() {
        String event1 = "first";
        String event2 = "second";

        entity.registerEvent(event1);
        entity.registerEvent(event2);

        List<Object> events = entity.getDomainEvents();
        assertEquals(2, events.size());
        assertEquals(event1, events.get(0));
        assertEquals(event2, events.get(1));
    }

    @Test
    @DisplayName("getDomainEvents — 반환된 리스트는 수정 불가능하다")
    void getDomainEvents_returns_unmodifiable_list() {
        entity.registerEvent("event");

        List<Object> events = entity.getDomainEvents();
        assertThrows(UnsupportedOperationException.class, () -> events.add("extra"));
    }

    @Test
    @DisplayName("clearDomainEvents — 이벤트 목록이 비워진다")
    void clearDomainEvents_empties_the_list() {
        entity.registerEvent("event1");
        entity.registerEvent("event2");

        entity.clearDomainEvents();

        assertTrue(entity.getDomainEvents().isEmpty());
    }

    @Test
    @DisplayName("초기 상태 — 이벤트 목록이 비어 있다")
    void initialState_has_no_events() {
        assertTrue(entity.getDomainEvents().isEmpty());
    }

    @Test
    @DisplayName("clearDomainEvents 후 새 이벤트를 다시 등록할 수 있다")
    void registerEvent_after_clear_works_correctly() {
        entity.registerEvent("old");
        entity.clearDomainEvents();
        entity.registerEvent("new");

        List<Object> events = entity.getDomainEvents();
        assertEquals(1, events.size());
        assertEquals("new", events.get(0));
    }
}
