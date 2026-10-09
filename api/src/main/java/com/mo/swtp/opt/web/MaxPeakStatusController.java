package com.mo.swtp.opt.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.opt.dto.MaxPeakStatusDto;
import com.mo.swtp.opt.service.MaxPeakStatusService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용량트렌드 3번섹션 — 최대 피크 현황 조회 REST API 컨트롤러.
 *
 * <p>{@code GET /api/opt/max-peak-status} — 입력 파라미터 없이 서버 시점 기준 현재 월 포함 최근 6개월의 월별
 * 최대 순시전력 피크(kW)를 6개 월 슬롯으로 표출한다 (사용량트렌드-3번섹션 PLAN1). 각 월 피크는 그 달의 분(分)별
 * 전체 활성 PWI 태그 합산값 중 최댓값이며, 데이터 없는 달은 {@code peakVal=null} 이다. 읽기 전용 집계 —
 * 단순 GET 폴링 방식, 입력 검증·400 응답 없음.</p>
 *
 * <p>2번섹션 {@link EnergyUsageTrendController}(전력량 추이) 와 같은 사용량트렌드 대시보드 전용이나 화면·의미가
 * 달라 별도 컨트롤러로 둔다 (Swagger {@code @Tag 16.} 그룹만 공유).</p>
 */
@Tag(name = "16. 사용량 트렌드")
@RestController
@RequestMapping("/api/opt/max-peak-status")
@RequiredArgsConstructor
public class MaxPeakStatusController extends CommonController {

    private final MaxPeakStatusService maxPeakStatusService;

    @Operation(summary = "사용량트렌드 3번섹션 최대 피크 현황 조회",
               description = "입력 파라미터 없이 서버 시점 기준 현재 월 포함 최근 6개월의 월별 최대 순시전력 피크(kW)를 "
                       + "반환한다. 각 월 피크는 전체 활성 순시전력(PWI, tag_se_cd='PWI', use_yn='Y', 태양광 발전 포함) 태그를 "
                       + "분(分)별로 합산(SUM(COALESCE(corr_val, raw_val)), GOOD only)한 값 중 그 달의 최댓값이다 "
                       + "(MAX_over_month(SUM_over_facilities(PWI per minute))). 응답은 항상 6개 월 슬롯(월 시작 일시 오름차순)이며 "
                       + "데이터 없는 달은 peakVal=null 이다. PWI 태그·데이터 부재 시 6슬롯 전부 null 을 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<MaxPeakStatusDto>> getMaxPeakStatus() {
        return getResponseEntity(maxPeakStatusService.getMaxPeakStatus());
    }
}
