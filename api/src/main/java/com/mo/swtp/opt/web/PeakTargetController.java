package com.mo.swtp.opt.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.opt.dto.PeakTargetDto;
import com.mo.swtp.opt.dto.PeakTargetUpsertDto;
import com.mo.swtp.opt.service.PeakTargetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전력피크 목표값 REST API 컨트롤러.
 *
 * <p>2 엔드포인트:</p>
 * <ul>
 *   <li>{@code GET /api/opt/peak-target} — 현재 목표 피크치 조회</li>
 *   <li>{@code PUT /api/opt/peak-target} — 목표 피크치 수정·저장 (트랜잭션 + 이벤트 발행 → SSE 전파)</li>
 * </ul>
 *
 * <p>SSE 구독 엔드포인트는 {@code PeakTargetSseController} 분리 (응답 타입 다름).</p>
 */
@Tag(name = "13. 전력피크 목표값")
@RestController
@RequestMapping("/api/opt/peak-target")
@RequiredArgsConstructor
public class PeakTargetController extends CommonController {

    private final PeakTargetService peakTargetService;

    @Operation(summary = "전력피크 목표값 조회",
               description = "시스템 전역 단일 목표 피크 전력값(kW). 0 = 미설정 (운전원 최초 저장 전).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류 (시드 미초기화 포함)")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<PeakTargetDto>> getPeakTarget() {
        return getResponseEntity(peakTargetService.getPeakTarget());
    }

    @Operation(summary = "전력피크 목표값 수정·저장",
               description = "양수 목표 피크치를 저장하면 같은 화면 모든 SSE 구독자에게 변경값이 전파된다. "
                       + "저장 흐름 — FOR UPDATE → change → publishEvent → commit → AFTER_COMMIT SSE.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (null·0·음수)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류 (시드 미초기화 포함)")
    })
    @PutMapping
    public ResponseEntity<CommonResponseDto<PeakTargetDto>> changePeakTarget(
            @Valid @RequestBody PeakTargetUpsertDto dto
    ) {
        return getResponseEntity(peakTargetService.changePeakTarget(dto));
    }
}
