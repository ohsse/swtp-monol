package com.mo.swtp.opt.sse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * 전력피크 목표값 변경 SSE 구독 컨트롤러.
 *
 * <p>전역 단일 채널 SSE 연결을 등록하고 보관소가 emitter 생명주기를 관리한다. 구독 경로는
 * 고정 경로 (path variable 없음) 이므로 JWT 필터의 exclude-paths 설정에 와일드카드 없이 그대로
 * 등록한다 (전력피크분석-1번섹션 PLAN1 §SSE 인프라 — proc 선례 동일 인증 우회).</p>
 *
 * <p>payload 비민감화 정합 — broadcast 페이로드는 targetPeakElpwr·updtDtm 2 필드만 허용
 * (사용자 정보·시설 운영 데이터 일체 제외).</p>
 */
@Tag(name = "13. 전력피크 목표값", description = "전력피크 목표값 변경 SSE 구독")
@RestController
@RequestMapping("/api/opt")
@RequiredArgsConstructor
public class PeakTargetSseController {

    private final PeakTargetSseService sseService;

    @Operation(
            summary = "전력피크 목표값 SSE 구독",
            description = "전력피크 분석 화면 진입 시 호출. 목표값 변경 이벤트가 발생하면 모든 구독자에게 동시 push."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "SSE 연결 성립 (Content-Type: text/event-stream)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping(value = "/peak-target/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return sseService.subscribe();
    }
}
