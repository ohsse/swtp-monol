package com.mo.swtp.proc.sse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * AI 운전모드 변경 SSE 구독 컨트롤러.
 *
 * <p>공정/제어대상별 SSE 연결을 등록하고 보관소가 emitter 생명주기를 관리한다.
 * 인증 우회 경로 (송수펌프제어분석-2번섹션 PLAN2 SSE 인증 1안) — JWT 필터의 exclude-paths
 * 설정에 동일 path 추가 의무 (AntPathMatcher 와일드카드 필수).</p>
 *
 * <p>payload 비민감화 정합 — broadcast 페이로드는 procId, aiDrvnModCd, startDtm 3 필드만 허용
 * (사용자 정보, 시설 운영 데이터 일체 제외). 향후 쿠키 인증 통합 사이클에서 우회 제거 1줄 가능.</p>
 */
@Tag(name = "10. AI 운전모드", description = "공정/제어대상별 AI 운전모드 SSE 구독")
@RestController
@RequestMapping("/api/proc")
@RequiredArgsConstructor
public class AiDrvnModeSseController {

    private final AiDrvnModeSseService sseService;

    @Operation(
            summary = "AI 운전모드 SSE 구독",
            description = "공정/제어대상 화면 진입 시 호출. 변경 이벤트가 발생하면 동일 procId 의 모든 구독자에게 동시 push."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE 연결 성립 (Content-Type: text/event-stream)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping(value = "/{procId}/ai-mode/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@PathVariable String procId) {
        return sseService.subscribe(procId);
    }
}
