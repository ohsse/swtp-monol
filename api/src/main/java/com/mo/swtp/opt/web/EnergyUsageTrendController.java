package com.mo.swtp.opt.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.opt.dto.EnergyUsageTrendDto;
import com.mo.swtp.opt.dto.EnergyUsageTrendSearchDto;
import com.mo.swtp.opt.service.EnergyUsageTrendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용량트렌드 2번섹션 — 정수장 전체 전력량 추이 조회 REST API 컨트롤러.
 *
 * <p>{@code GET /api/opt/energy-usage-trend} — 사용량트렌드 대시보드 1번섹션 파라미터 3종(집계단위·시작일·
 * 종료일)을 받아, 정수장을 구성하는 모든 계측기가 보유한 적산전력량(PWQ) 태그의 버킷 단위 전력량(kWh) 시계열을
 * 단일 응답으로 표출한다 (사용량트렌드-2번섹션 PLAN1). 읽기 전용 집계 — 단순 GET 폴링 방식.</p>
 *
 * <p>전력피크분석({@code @Tag 14.}) 과 의미·화면이 다른 사용량트렌드 대시보드 전용이므로 별도 컨트롤러로 둔다.
 * 향후 동 대시보드 다른 섹션 엔드포인트가 본 컨트롤러에 누적될 수 있다.</p>
 */
@Tag(name = "16. 사용량 트렌드")
@RestController
@RequestMapping("/api/opt/energy-usage-trend")
@RequiredArgsConstructor
public class EnergyUsageTrendController extends CommonController {

    private final EnergyUsageTrendService energyUsageTrendService;

    @Operation(summary = "사용량트렌드 2번섹션 정수장 전체 전력량 추이 조회",
               description = "집계단위(시/일/월)·시작일·종료일을 받아 정수장 전체 활성 적산전력량(PWQ, tag_se_cd='PWQ', "
                       + "use_yn='Y') 태그의 버킷별 전력량(kWh) 시계열을 반환한다. 조회 기간은 시작일 00:00 ~ 종료일 익일 "
                       + "00:00(배타적 상한)으로 종료일 당일 데이터를 모두 포함한다. 각 버킷 전력량은 태그별 적산값 "
                       + "MAX(raw_val)-MIN(raw_val) 차분(GOOD only, corr_val 미사용) 후 버킷 단위 전역 합산이며, 데이터 없는 "
                       + "버킷은 생략(sparse)한다. PWQ 태그·데이터 부재 시 빈 시계열(points=[]) 을 반환한다. "
                       + "집계단위 YEAR·기간 역전·null·13개월 초과 시 400(INVALID_SEARCH_PERIOD).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (집계단위 YEAR·기간 역전·null·13개월 초과)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<EnergyUsageTrendDto>> getEnergyUsageTrend(
            @ModelAttribute EnergyUsageTrendSearchDto searchDto) {
        return getResponseEntity(energyUsageTrendService.getEnergyUsageTrend(searchDto));
    }
}
