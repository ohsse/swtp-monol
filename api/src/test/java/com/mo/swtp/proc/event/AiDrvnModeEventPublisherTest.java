package com.mo.swtp.proc.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

import com.mo.swtp.proc.domain.AiDrvnMode;
import com.mo.swtp.proc.domain.Process;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import com.mo.swtp.proc.repository.AiDrvnModeRepository;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

/**
 * {@link AiDrvnModeEventPublisher} 단위 테스트.
 *
 * <p>{@link com.mo.swtp.common.event.AbstractDomainEventPublisher} 위임 흐름 검증.
 * Spring 컨텍스트 미사용 — Mockito 격리.</p>
 */
@ExtendWith(MockitoExtension.class)
class AiDrvnModeEventPublisherTest {

    @Mock
    private AiDrvnModeRepository repository;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @Test
    void changeAndPublish_엔티티의_축적된_이벤트가_발행되고_클리어된다() {
        // given: 시드 'PUMP_CONTROL' Process + create() 가 이벤트 1건 축적한 AiDrvnMode
        Process process = Process.create("PUMP_CONTROL", "송수펌프제어", 1);
        AiDrvnMode mode = AiDrvnMode.create(process, AiDrvnModeCode.AI, LocalDateTime.now());
        // change() 호출로 이벤트 1건 추가 — 총 2건 축적
        mode.change(AiDrvnModeCode.AI_RECOMD, LocalDateTime.now());
        assertThat(mode.getDomainEvents()).hasSize(2);

        AiDrvnModeEventPublisher publisher =
                new AiDrvnModeEventPublisher(repository, applicationEventPublisher);

        // when: changeAndPublish 위임
        publisher.changeAndPublish(mode);

        // then: 축적 이벤트 2건이 ApplicationEventPublisher 로 발행 + 엔티티 이벤트 클리어
        then(applicationEventPublisher).should(times(2))
                .publishEvent(any(AiDrvnModeChangedEvent.class));
        assertThat(mode.getDomainEvents()).isEmpty();
        // changeAndPublish 는 repository.save 호출하지 않음 (dirty checking 위임)
        then(repository).should(never()).save(any());
    }

    @Test
    void createAndPublish_엔티티가_저장되고_축적된_이벤트가_발행된다() {
        // given: create() 가 이벤트 1건 축적한 신규 AiDrvnMode
        Process process = Process.create("PUMP_CONTROL", "송수펌프제어", 1);
        AiDrvnMode newMode = AiDrvnMode.create(process, AiDrvnModeCode.AI, LocalDateTime.now());
        assertThat(newMode.getDomainEvents()).hasSize(1);

        given(repository.save(newMode)).willReturn(newMode);

        AiDrvnModeEventPublisher publisher =
                new AiDrvnModeEventPublisher(repository, applicationEventPublisher);

        // when: createAndPublish 위임
        AiDrvnMode result = publisher.createAndPublish(newMode);

        // then: save 1회 + 축적 이벤트 1건 발행 + 클리어
        then(repository).should().save(newMode);
        then(applicationEventPublisher).should()
                .publishEvent(any(AiDrvnModeChangedEvent.class));
        assertThat(result.getDomainEvents()).isEmpty();
    }
}
