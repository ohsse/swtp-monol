package com.mo.swtp.instrument.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.instrument.dto.PumpOperationHistoryDto;
import com.mo.swtp.instrument.dto.PumpPeriodSearchDto;
import com.mo.swtp.instrument.service.PumpOperationHistoryService;
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
 * 송수펌프 가동이력 4번섹션 — 펌프 가동상태 타임라인 차트 조회 API 컨트롤러.
 *
 * <p>1번섹션 파라미터 중 from~to 날짜만 받아 조회기간 동안 각 펌프의 가동(ON) 구간 세그먼트를 반환하는 읽기 전용
 * 엔드포인트. 프론트는 응답 세그먼트를 Chart.js floating bar 막대로 표출한다 (송수펌프가동이력_4번섹션 PLAN1
 * §구현 방향 5). 3번섹션({@link PumpTimeSeriesController}) 의 조회 단위(inqUnit) 와 버킷 집계를 쓰지 않고
 * from~to 원시 OPS 상태의 ON 구간 런렝스 압축만 수행한다.</p>
 *
 * <p>계측기 CRUD ({@link InstrumentController}) · 2번섹션 ({@link PumpOperationRateController}) ·
 * 3번섹션 ({@link PumpTimeSeriesController}) 과 같은 {@code /api/instrument} 기준 경로를 사용하나, 리터럴
 * 세그먼트({@code /pump-operation-history}) 가 {@code /{instrumentId}} path-variable 보다 Spring PathPattern
 * 특이도가 높아 우선 매칭된다 (ambiguous-mapping 미발생 — 2·3번섹션 선례). 기존 컨트롤러를 확장하지 않고 신규
 * 클래스로 분리한다 ({@code @Tag} 문자열만 재사용).</p>
 *
 * <p>송수펌프가동이력_4번섹션 ANALYZE1·PLAN1 (2026-06-02) 도입.</p>
 */
@Tag(name = "11. 송수펌프 가동이력")
@RestController
@RequestMapping("/api/instrument")
@RequiredArgsConstructor
public class PumpOperationHistoryController extends CommonController {

    private final PumpOperationHistoryService pumpOperationHistoryService;

    @Operation(summary = "송수펌프 가동상태 타임라인 조회 (4번섹션 막대 차트)",
            description = "전체 활성 송수펌프(use_yn=Y)의 조회기간 가동상태(OPS)를 펌프별 가동(ON) 구간 세그먼트로 "
                    + "반환한다. 각 세그먼트는 GOOD 품질 raw_val=1(ON) 인 연속 구간이며, OFF·BAD·UNCERTAIN·결측 "
                    + "구간은 세그먼트를 생성하지 않는다(화면상 빈 공간). OPS 는 Hold Last Value 미적용으로 통신단절 "
                    + "구간을 ON 으로 잇지 않는다. 세그먼트 종료 시각은 마지막 ON 수집 시각 + 1분(수집 주기). "
                    + "OPS 태그가 없는 펌프는 segments 빈 배열.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (기간 결측 또는 from > to)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "리소스 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/pump-operation-history")
    public ResponseEntity<CommonResponseDto<List<PumpOperationHistoryDto>>> findPumpOperationHistory(
            @ModelAttribute PumpPeriodSearchDto search) {
        return getResponseEntity(pumpOperationHistoryService.findPumpOperationHistory(search));
    }
}
