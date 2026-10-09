package com.mo.swtp.instrument.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.instrument.dto.PumpCtrlHistoryDto;
import com.mo.swtp.instrument.dto.PumpCtrlStatDto;
import com.mo.swtp.instrument.dto.PumpPeriodSearchDto;
import com.mo.swtp.instrument.service.PumpCtrlHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 송수펌프 제어이력 2·3번 섹션 — AI 운영 현황 통계 + 제어 이력 목록 조회 API 컨트롤러.
 *
 * <p>1번섹션 from~to 조회기간을 받아 같은 제어이력 테이블({@code pump_ctrl_h})을 두 방식으로 표출하는 읽기
 * 전용 엔드포인트 2건 — 2번섹션({@code /pump-ctrl-stat})은 운전모드별 카운트·비율 통계, 3번섹션
 * ({@code /pump-ctrl-history})은 시간 역순 목록 (제어이력 재도입 PLAN1 §구현 방향 Phase 3·4).</p>
 *
 * <p>계측기 CRUD ({@link InstrumentController}) · 가동이력 2~4번섹션과 같은 {@code /api/instrument} 기준
 * 경로를 사용하나, 리터럴 세그먼트({@code /pump-ctrl-stat}·{@code /pump-ctrl-history})가
 * {@code /{instrumentId}} path-variable 보다 Spring PathPattern 특이도가 높아 우선 매칭된다
 * (ambiguous-mapping 미발생 — 가동이력 선례). 기존 컨트롤러를 확장하지 않고 신규 클래스로 분리한다.</p>
 *
 * <p>제어이력 재도입 ANALYZE1·PLAN1 (2026-06-04) 도입.</p>
 */
@Tag(name = "12. 송수펌프 제어이력")
@RestController
@RequestMapping("/api/instrument")
@RequiredArgsConstructor
public class PumpCtrlHistoryController extends CommonController {

    private final PumpCtrlHistoryService pumpCtrlHistoryService;

    @Operation(summary = "송수펌프 AI 운영 현황 통계 조회 (2번섹션 차트/표)",
            description = "조회기간 동안의 제어 이력을 운전모드(AI/AI추천/AI분석)별로 카운트하고 비율(모드 카운트 ÷ "
                    + "전체 카운트, %)을 반환한다. 수동 제어(ai_drvn_mod NULL)는 집계에서 제외되며, 전체 카운트는 "
                    + "AI 운전모드가 부여된 이력의 합이다. AI/AI추천/AI분석 3종은 카운트 0건이어도 항상 포함한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (기간 결측 또는 from > to)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "리소스 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/pump-ctrl-stat")
    public ResponseEntity<CommonResponseDto<PumpCtrlStatDto>> findCtrlStat(
            @ModelAttribute PumpPeriodSearchDto search) {
        return getResponseEntity(pumpCtrlHistoryService.findCtrlStat(search));
    }

    @Operation(summary = "송수펌프 제어 이력 목록 조회 (3번섹션 표)",
            description = "조회기간 동안의 제어 이력을 시간 역순(제어요청시간 내림차순)으로 반환한다. 각 행은 "
                    + "제어요청시간·제어대상펌프명·제어 태그번호(tag_se_cd=CMD, io_cd=OUTPUT — 미존재 시 null)·"
                    + "제어요청구분(가동/중지)·제어결과(제어완료/제어취소)·갱신시간(제어완료시간)·운전모드(수동 시 null)"
                    + "로 구성된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (기간 결측 또는 from > to)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "리소스 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/pump-ctrl-history")
    public ResponseEntity<CommonResponseDto<List<PumpCtrlHistoryDto>>> findCtrlHistory(
            @ModelAttribute PumpPeriodSearchDto search) {
        return getResponseEntity(pumpCtrlHistoryService.findCtrlHistory(search));
    }
}
