package com.mo.swtp.opt.sse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.mo.swtp.opt.event.PeakTargetChangedEvent;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@link PeakTargetSseService} 단위 테스트 — Spring 컨텍스트 미사용.
 *
 * <p>전역 단일 채널 보관소의 등록·카운트·broadcast 무예외를 검증한다. onCompletion/onTimeout/onError
 * 자기정리는 Spring MVC 런타임이 콜백을 발화하는 동작이므로 (emitter 비초기화 상태에서는 콜백 미발화)
 * 컨트롤러 통합 테스트 + 수동 2탭 검증 (PLAN1) 범위로 위임한다.</p>
 */
class PeakTargetSseServiceTest {

    private PeakTargetSseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new PeakTargetSseService();
    }

    @Test
    void subscribe는_SseEmitter를_반환하고_보관소에_등록한다() {
        SseEmitter emitter = sseService.subscribe();

        assertThat(emitter).isNotNull();
        assertThat(sseService.count()).isEqualTo(1);
    }

    @Test
    void 다중_subscriber가_전역_단일_채널에_등록된다() {
        sseService.subscribe();
        sseService.subscribe();
        sseService.subscribe();

        assertThat(sseService.count()).isEqualTo(3);
    }

    @Test
    void broadcast는_구독자가_없으면_조용히_종료한다() {
        PeakTargetChangedEvent event = new PeakTargetChangedEvent(
                new BigDecimal("900.0000"), LocalDateTime.now());

        assertThatCode(() -> sseService.broadcast(event)).doesNotThrowAnyException();
        assertThat(sseService.count()).isEqualTo(0);
    }

    @Test
    void 다중_구독자_등록_후_broadcast가_예외없이_수행된다() {
        sseService.subscribe();
        sseService.subscribe();
        assertThat(sseService.count()).isEqualTo(2);

        PeakTargetChangedEvent event = new PeakTargetChangedEvent(
                new BigDecimal("1234.5678"), LocalDateTime.now());

        assertThatCode(() -> sseService.broadcast(event)).doesNotThrowAnyException();
        assertThat(sseService.count()).isEqualTo(2);
    }
}
