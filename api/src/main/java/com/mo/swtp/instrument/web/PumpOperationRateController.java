package com.mo.swtp.instrument.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.instrument.dto.PumpOperationRateDto;
import com.mo.swtp.instrument.service.PumpOperationRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 송수펌프 가동이력 2번섹션 — 펌프 상태 카드 조회 API 컨트롤러.
 *
 * <p>전체 활성 송수펌프의 현재 상태(제원값 + 가동률)를 카드 단위로 반환하는 읽기 전용 엔드포인트.
 * 계측기 CRUD ({@link InstrumentController}) 와 같은 {@code /api/instrument} 기준 경로를 사용하나,
 * 리터럴 세그먼트 {@code /pump-operation-rate} 가 {@code /{instrumentId}} path-variable 보다 Spring
 * PathPattern 특이도가 높아 우선 매칭된다 (ambiguous-mapping 미발생 — 송수펌프가동이력_2번섹션 PLAN1 §Phase 4).</p>
 *
 * <p>송수펌프가동이력_2번섹션 ANALYZE1·PLAN1 (2026-06-02) 도입.</p>
 */
@Tag(name = "11. 송수펌프 가동이력")
@RestController
@RequestMapping("/api/instrument")
@RequiredArgsConstructor
public class PumpOperationRateController extends CommonController {

    private final PumpOperationRateService pumpOperationRateService;

    @Operation(summary = "송수펌프 상태 카드 목록 조회 (2번섹션)",
            description = "전체 활성 송수펌프(use_yn=Y)의 현재 상태 카드를 dispOrd → instrumentNm 순으로 반환한다. "
                    + "각 카드는 펌프명·정격양정(m)·정격유량(m³/h)·가동률(%)을 노출한다. "
                    + "가동률 산정 — 정격펌프: OPS GOOD On(1)→100·Off(0)→0, 인버터펌프: FQI GOOD 시 현재 주파수값(Hz)을 그대로 %로. "
                    + "판정 태그 부재·SCADA 품질 불량(BAD/UNCERTAIN)·미정의 가동상태 시 oprtngRate=null "
                    + "(판정 태그 품질 qualityCd·수집시각 acqDtm 동봉).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/pump-operation-rate")
    public ResponseEntity<CommonResponseDto<List<PumpOperationRateDto>>> findPumpOperationRates() {
        return getResponseEntity(pumpOperationRateService.findPumpOperationRates());
    }
}
