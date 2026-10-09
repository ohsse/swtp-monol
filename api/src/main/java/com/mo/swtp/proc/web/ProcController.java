package com.mo.swtp.proc.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.proc.dto.AiDrvnModeDto;
import com.mo.swtp.proc.dto.AiDrvnModeHistoryDto;
import com.mo.swtp.proc.dto.AiDrvnModeUpsertDto;
import com.mo.swtp.proc.dto.ProcDto;
import com.mo.swtp.proc.exception.ProcErrorCode;
import com.mo.swtp.proc.service.AiDrvnModeService;
import com.mo.swtp.proc.service.ProcessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공정/제어대상 · AI 운전모드 REST API 컨트롤러.
 *
 * <p>4 엔드포인트:</p>
 * <ul>
 *   <li>{@code GET  /api/proc} — 공정/제어대상 마스터 목록 (활성)</li>
 *   <li>{@code GET  /api/proc/{procId}/ai-mode} — 현재 AI 운전모드 조회</li>
 *   <li>{@code PUT  /api/proc/{procId}/ai-mode} — AI 운전모드 변경 (트랜잭션 + 이벤트 발행)</li>
 *   <li>{@code GET  /api/proc/{procId}/ai-mode/history} — 변경 이력 페이지네이션</li>
 * </ul>
 *
 * <p>SSE 구독 엔드포인트는 {@code AiDrvnModeSseController} 분리 (응답 타입 다름).</p>
 */
@Tag(name = "10. AI 운전모드")
@RestController
@RequestMapping("/api/proc")
@RequiredArgsConstructor
public class ProcController extends CommonController {

    private final ProcessService processService;
    private final AiDrvnModeService aiDrvnModeService;

    @Operation(summary = "공정/제어대상 목록 조회 (활성)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<List<ProcDto>>> findAll() {
        return getResponseEntity(processService.findAllActive());
    }

    @Operation(summary = "AI 운전모드 현재 상태 조회",
               description = "공정/제어대상별 현재 모드 (최초 설정 전이면 404)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "현재 모드 미존재 또는 공정/제어대상 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{procId}/ai-mode")
    public ResponseEntity<CommonResponseDto<AiDrvnModeDto>> findCurrentMode(
            @PathVariable String procId
    ) {
        AiDrvnModeDto dto = aiDrvnModeService.findCurrent(procId)
                .orElseThrow(() -> new RestApiException(ProcErrorCode.PROC_NOT_FOUND));
        return getResponseEntity(dto);
    }

    @Operation(summary = "AI 운전모드 변경",
               description = "트랜잭션 6단계 — FOR UPDATE → UPDATE end_dtm → UPSERT _p → INSERT _h → publishEvent → commit. 커밋 후 SSE 전파.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "공정/제어대상 없음"),
            @ApiResponse(responseCode = "409", description = "동시 변경 충돌 (부분 UNIQUE 인덱스)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/{procId}/ai-mode")
    public ResponseEntity<CommonResponseDto<AiDrvnModeDto>> changeMode(
            @PathVariable String procId,
            @Valid @RequestBody AiDrvnModeUpsertDto dto
    ) {
        return getResponseEntity(aiDrvnModeService.changeAiDrvnMode(procId, dto.getAiDrvnModCd()));
    }

    @Operation(summary = "AI 운전모드 변경 이력 (페이지네이션)",
               description = "공정/제어대상별 변경 이력 — start_dtm DESC 정렬")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{procId}/ai-mode/history")
    public ResponseEntity<CommonResponseDto<Page<AiDrvnModeHistoryDto>>> findHistory(
            @PathVariable String procId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "startDtm"));
        return getResponseEntity(aiDrvnModeService.findHistory(procId, pageable));
    }
}
