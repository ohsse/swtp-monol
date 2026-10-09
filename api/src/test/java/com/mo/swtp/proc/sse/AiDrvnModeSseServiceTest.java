package com.mo.swtp.proc.sse;

import static org.assertj.core.api.Assertions.assertThat;

import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import com.mo.swtp.proc.event.AiDrvnModeChangedEvent;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * {@link AiDrvnModeSseService} 단위 테스트 — Spring 컨텍스트 미사용.
 */
class AiDrvnModeSseServiceTest {

    private AiDrvnModeSseService sseService;

    @BeforeEach
    void setUp() {
        sseService = new AiDrvnModeSseService();
    }

    @Test
    void subscribe는_SseEmitter를_반환하고_보관소에_등록한다() {
        SseEmitter emitter = sseService.subscribe("PUMP_CONTROL");

        assertThat(emitter).isNotNull();
        assertThat(sseService.count("PUMP_CONTROL")).isEqualTo(1);
    }

    @Test
    void 동일_procId에_다중_subscriber가_등록된다() {
        sseService.subscribe("PUMP_CONTROL");
        sseService.subscribe("PUMP_CONTROL");
        sseService.subscribe("PUMP_CONTROL");

        assertThat(sseService.count("PUMP_CONTROL")).isEqualTo(3);
    }

    @Test
    void broadcast는_등록되지_않은_procId에_조용히_종료한다() {
        AiDrvnModeChangedEvent event = new AiDrvnModeChangedEvent(
                "UNKNOWN_PROC", AiDrvnModeCode.AI, LocalDateTime.now()
        );

        sseService.broadcast(event);  // 예외 미발생

        assertThat(sseService.count("UNKNOWN_PROC")).isEqualTo(0);
    }

    @Test
    void 서로_다른_procId는_보관소에서_독립적으로_관리된다() {
        sseService.subscribe("PUMP_CONTROL");
        sseService.subscribe("WTR_TREAT");
        sseService.subscribe("PUMP_CONTROL");

        assertThat(sseService.count("PUMP_CONTROL")).isEqualTo(2);
        assertThat(sseService.count("WTR_TREAT")).isEqualTo(1);
    }
}
