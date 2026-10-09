package com.mo.swtp.proc.sse;

import com.mo.swtp.proc.event.AiDrvnModeChangedEvent;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 운전모드 변경 SSE 전파 서비스.
 *
 * <p>공정/제어대상별 {@link SseEmitter} 보관소 + 변경 이벤트 broadcast + heartbeat 송신.
 * 모든 사용자가 동일 공정/제어대상 화면에서 같은 모드 상태를 공유해야 하므로 SSE 단방향 push 로
 * 변경 사실을 전파한다 (송수펌프제어분석-2번섹션 ANALYZE2 안건 2 + PLAN2 §SSE 인프라 설계).</p>
 *
 * <p>구조:</p>
 * <ul>
 *   <li>보관소: {@code ConcurrentHashMap<procId, CopyOnWriteArrayList<SseEmitter>>}
 *       (procId 별 다중 subscriber)</li>
 *   <li>타임아웃: 30 분 — proxy idle timeout 회피 + 메모리 누수 방지</li>
 *   <li>heartbeat: 15 초 keep-alive (proxy 기본 60초 idle 회피)</li>
 *   <li>자기 정리: onCompletion/onTimeout/onError 시 보관소에서 자동 제거</li>
 * </ul>
 *
 * <p>멀티 인스턴스 확장 시 Redis pub/sub 등 외부 메시지 브로커 필요 — 본 사이클은 단일 인스턴스 가정
 * (multi-tenant.md — 지자체별 단일 backend 인스턴스 정렬).</p>
 */
@Service
@Slf4j
public class AiDrvnModeSseService {

    /** SSE 연결 타임아웃 — 30 분 */
    static final long EMITTER_TIMEOUT_MS = 30L * 60L * 1000L;

    /** heartbeat 주기 — 15 초 (proxy idle timeout 회피) */
    static final long HEARTBEAT_INTERVAL_MS = 15L * 1000L;

    /** 보관소 — procId 별 다중 SseEmitter */
    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    /**
     * 공정/제어대상 SSE 구독을 등록한다.
     *
     * <p>onCompletion/onTimeout/onError 콜백으로 보관소에서 자동 제거된다.</p>
     *
     * @param procId 공정/제어대상 ID
     * @return 등록된 SseEmitter (Spring MVC 가 응답 스트림 유지)
     */
    public SseEmitter subscribe(String procId) {
        SseEmitter emitter = new SseEmitter(EMITTER_TIMEOUT_MS);
        emitters.computeIfAbsent(procId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(procId, emitter, "completion"));
        emitter.onTimeout(() -> remove(procId, emitter, "timeout"));
        emitter.onError(throwable -> remove(procId, emitter, "error: " + throwable.getMessage()));

        // 등록 직후 init comment 송신 — Spring MVC 가 status 200 + Content-Type 헤더를
        // 즉시 client 에게 commit 하도록 강제 (첫 send() 까지 응답 보류 회피)
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            remove(procId, emitter, "init send fail");
        }

        log.debug("SSE 구독 등록 — procId={}, 현재 subscriber={}", procId, count(procId));
        return emitter;
    }

    /**
     * 변경 이벤트를 해당 공정/제어대상의 모든 subscriber 에게 send 한다.
     *
     * <p>{@code AiDrvnModeChangedEventListener} 가 트랜잭션 commit 후 호출한다.
     * send 실패한 emitter 는 보관소에서 즉시 제거된다.</p>
     *
     * @param event 변경 이벤트 (procId+aiDrvnModCd+startDtm)
     */
    public void broadcast(AiDrvnModeChangedEvent event) {
        List<SseEmitter> targets = emitters.get(event.procId());
        if (targets == null || targets.isEmpty()) {
            return;
        }
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event()
                        .name("ai-drvn-mod-changed")
                        .data(event));
            } catch (IOException e) {
                log.warn("SSE send 실패 — procId={}, error={}", event.procId(), e.getMessage());
                remove(event.procId(), emitter, "send error");
            }
        }
    }

    /**
     * heartbeat ping 송신 ({@code @Scheduled} 15초 주기).
     *
     * <p>SSE comment 라인 (콜론 시작) 으로 keep-alive 만 전송. payload 비민감화 정합 (PLAN2).</p>
     */
    @Scheduled(fixedRate = HEARTBEAT_INTERVAL_MS)
    public void sendPing() {
        for (Map.Entry<String, List<SseEmitter>> entry : emitters.entrySet()) {
            String procId = entry.getKey();
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().comment("ping"));
                } catch (IOException e) {
                    remove(procId, emitter, "ping error");
                }
            }
        }
    }

    /** 보관소에서 emitter 제거 (자기 정리). */
    private void remove(String procId, SseEmitter emitter, String reason) {
        List<SseEmitter> list = emitters.get(procId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emitters.remove(procId);
            }
        }
        log.debug("SSE 제거 — procId={}, reason={}, 잔여={}", procId, reason, count(procId));
    }

    /** 테스트 가시성 위한 현재 subscriber 수 조회. */
    public int count(String procId) {
        List<SseEmitter> list = emitters.get(procId);
        return list == null ? 0 : list.size();
    }
}
