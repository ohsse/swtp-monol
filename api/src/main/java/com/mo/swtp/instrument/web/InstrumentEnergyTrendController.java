package com.mo.swtp.instrument.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.instrument.dto.InstrumentEnergyTrendDto;
import com.mo.swtp.instrument.dto.InstrumentEnergyTrendSearchDto;
import com.mo.swtp.instrument.service.InstrumentEnergyTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 설비별 사용량 4번섹션 — 계측기 전력량 트렌드 조회 API 컨트롤러.
 *
 * <p>3번섹션에서 선택한 단일 계측기({@code instrumentId})와 1번섹션 파라미터 3종(집계단위·시작/종료일자)을 받아
 * 조회기간 전력량(kWh) 시계열을 반환하는 읽기 전용 엔드포인트 (설비별사용량-4번섹션 PLAN1 §구현 방향 5).</p>
 *
 * <p>계측기 CRUD({@link InstrumentController}) · 송수펌프 시계열({@link PumpTimeSeriesController}) 과 같은
 * {@code /api/instrument} 기준 경로를 사용하나, SRP·{@code @Tag} 목적 분리를 위해 신규 클래스로 분리한다.
 * 리터럴 세그먼트({@code /{instrumentId}/energy-trend})는 {@code /{instrumentId}} GET 보다 Spring PathPattern
 * 특이도가 높아 우선 매칭되므로 ambiguous-mapping 이 발생하지 않는다.</p>
 */
@Tag(name = "15. 설비별 사용량")
@RestController
@RequestMapping("/api/instrument")
@RequiredArgsConstructor
public class InstrumentEnergyTrendController extends CommonController {

    private final InstrumentEnergyTrendService instrumentEnergyTrendService;

    @Operation(summary = "계측기 전력량 트렌드 조회 (4번섹션 설비 트렌드)",
            description = "3번섹션에서 선택한 계측기의 조회기간 전력량(kWh)을 단일 시계열로 반환한다. "
                    + "x축 버킷은 조회 단위(시/일/월)에 따라 변동한다. 각 버킷 전력량은 PWQ(적산전력량) 태그의 "
                    + "GOOD 품질 raw_val 차분(MAX-MIN)으로 산정하며(corr_val 미사용), 계측기가 PWQ 태그를 다건 "
                    + "보유하면 동일 버킷끼리 합산한다. 음수 차분(적산 리셋·롤오버) 버킷은 생략한다. 데이터 없는 "
                    + "버킷·PWQ 태그 부재 계측기는 points 빈 배열.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (조회 단위·기간 결측, from > to, YEAR, 396일 초과)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "리소스 없음 (미존재·비활성 계측기)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{instrumentId}/energy-trend")
    public ResponseEntity<CommonResponseDto<InstrumentEnergyTrendDto>> findEnergyTrend(
            @PathVariable String instrumentId,
            @ModelAttribute InstrumentEnergyTrendSearchDto search) {
        return getResponseEntity(instrumentEnergyTrendService.findEnergyTrend(instrumentId, search));
    }
}
