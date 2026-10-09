package com.mo.swtp.opt.sse;

import com.mo.swtp.opt.event.PeakTargetChangedEvent;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 전력피크 목표값 변경 SSE 전파 서비스.
 *
 * <p>{@link SseEmitter} 보관소 + 변경 이벤트 broadcast + heartbeat 송신. 정수장(테넌트) 전역 단일
 * 목표 피크치이므로 같은 화면을 구독하는 모든 사용자가 같은 값을 공유해야 한다. 변경 시 SSE 단방향
 * push 로 변경 사실을 전파한다 (전력피크분석-1번섹션 ANALYZE1 안건 + PLAN1 §SSE 인프라 설계).</p>
 *
 * <p>구조 — proc 도메인 ({@code AiDrvnModeSseService}) 의 공정/제어대상별
 * {@code ConcurrentHashMap<procId, List>} 보관소를 전역 단일 값에 맞춰 <b>단일
 * {@link CopyOnWriteArrayList}</b> 로 단순화한다 (coding-discipline §2 단순성 — 채널 키 불필요):</p>
 * <ul>
 *   <li>보관소: 전역 단일 {@code CopyOnWriteArrayList<SseEmitter>}</li>
 *   <li>타임아웃: 30 분 — proxy idle timeout 회피 + 메모리 누수 방지</li>
 *   <li>heartbeat: 15 초 keep-alive (proxy 기본 60초 idle 회피)</li>
 *   <li>자기 정리: onCompletion/onTimeout/onError 시 보관소에서 자동 제거</li>
 * </ul>
 *
 * <p>{@code @Scheduled} 는 {@code com.mo.swtp.proc.sse.SseConfig} 의 {@code @EnableScheduling}
 * 앱 전역 활성에 편승한다 (ANALYZE1 안건 3 — opt 전용 config 신설 금지). 멀티 인스턴스 확장 시
 * Redis pub/sub 필요 — 본 사이클은 지자체별 단일 backend 인스턴스 가정 (multi-tenant.md 정렬).</p>
 */
@Service
@Slf4j
public class PeakTargetSseService {

    /** SSE 연결 타임아웃 — 30 분 */
    static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;

    /** heartbeat 주기 — 15 초 (proxy idle timeout 회피) */
    static final long HEARTBEAT_INTERVAL_MS = 15L * 1000L;

    /** SSE 이벤트명 — 전역 단일 채널 */
    static final String EVENT_NAME = "peak-target-changed";

    /** 보관소 — 전역 단일 SseEmitter 리스트 */
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * 전력피크 목표값 SSE 구독을 등록한다.
     *
     * <p>onCompletion/onTimeout/onError 콜백으로 보관소에서 자동 제거된다.</p>
     *
     * @return 등록된 SseEmitter (Spring MVC 가 응답 스트림 유지)
     */
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.add(emitter);

        emitter.onCompletion(() -> remove(emitter, "completion"));
        emitter.onTimeout(() -> remove(emitter, "timeout"));
        emitter.onError(throwable -> remove(emitter, "error: " + throwable.getMessage()));

        // 등록 직후 init comment 송신 — Spring MVC 가 status 200 + Content-Type 헤더를
        // 즉시 client 에게 commit 하도록 강제 (첫 send() 까지 응답 보류 회피)
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove(emitter, "init send fail");
        }

        log.debug("전력피크 목표값 SSE 구독 등록 — 현재 subscriber={}", count());
        return emitter;
    }

    /**
     * 변경 이벤트를 모든 subscriber 에게 send 한다.
     *
     * <p>{@code PeakTargetChangedEventListener} 가 트랜잭션 commit 후 호출한다.
     * send 실패한 emitter 는 보관소에서 즉시 제거된다.</p>
     *
     * @param event 변경 이벤트 (targetPeakElpwr+updtDtm)
     */
    public void broadcast(PeakTargetChangedEvent event) {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(EVENT_NAME)
                        .data(event));
            } catch (IOException e) {
                log.warn("전력피크 목표값 SSE send 실패 — error={}", e.getMessage());
                remove(emitter, "send error");
            }
        }
    }

    /**
     * heartbeat ping 송신 ({@code @Scheduled} 15초 주기).
     *
     * <p>SSE comment 라인 (콜론 시작) 으로 keep-alive 만 전송. payload 비민감화 정합 (PLAN1).</p>
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendPing() {
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
            } catch (IOException e) {
                remove(emitter, "ping error");
            }
        }
    }

    /** 보관소에서 emitter 제거 (자기 정리). */
    private void remove(SseEmitter emitter, String reason) {
        emitters.remove(emitter);
        log.debug("전력피크 목표값 SSE 제거 — reason={}, 잔여={}", reason, count());
    }

    /** 테스트 가시성 위한 현재 subscriber 수 조회. */
    public int count() {
        return emitters.size();
    }
}
