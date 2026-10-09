package com.mo.swtp.facility.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendDto;
import com.mo.swtp.facility.dto.FacilityInstrumentPowerTrendSearchDto;
import com.mo.swtp.facility.service.FacilityInstrumentPowerTrendService;
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
 * 설비별 사용량 7번섹션 — 설비별 순시전력 트렌드 조회 API 컨트롤러.
 *
 * <p>2번섹션에서 선택한 시설({@code facilityId})과 1번섹션 파라미터(시작/종료일자)를 받아, 그 시설을 루트로 하는
 * 재귀 하위 트리 전체의 PWI(순시전력) 태그 보유 설비별 순시전력(kW) 1분 시계열을 멀티시리즈로 반환하는 읽기 전용
 * 엔드포인트 (설비별사용량-7번섹션 PLAN1 §구현 방향 5).</p>
 *
 * <p>{@link FacilityController}(시설 CRUD + 2·3번섹션 조회)·{@link FacilityInstrumentEnergyUsageController}(5·6번섹션
 * 누적 전력량·분포율)와 같은 {@code /api/facility} 기준 경로를 사용하나, SRP·{@code @Tag} 목적 분리를 위해 신규
 * 클래스로 분리한다. 리터럴 세그먼트({@code /{facilityId}/instrument-power-trend})는 {@code /{facilityId}} GET 보다
 * Spring PathPattern 특이도가 높아 우선 매칭되므로 ambiguous-mapping 이 발생하지 않는다.</p>
 */
@Tag(name = "15. 설비별 사용량")
@RestController
@RequestMapping("/api/facility")
@RequiredArgsConstructor
public class FacilityInstrumentPowerTrendController extends CommonController {

    private final FacilityInstrumentPowerTrendService facilityInstrumentPowerTrendService;

    @Operation(summary = "설비별 순시전력 트렌드 조회 (7번섹션)",
            description = "2번섹션에서 선택한 시설을 루트로 parent_facility_id self-FK 를 재귀 탐색(하위 시설 전체)한 "
                    + "뒤, 그 시설들의 활성 계측기 중 PWI(순시전력) 태그 보유 계측기별 조회기간(fromDt 00:00:00 ~ "
                    + "toDt 23:59:59) 순시전력(kW) 1분 시계열을 설비별 멀티시리즈로 반환한다. 한 설비가 PWI 태그를 "
                    + "다건 보유하면 동일 acq_dtm 끼리 합산(SUM(MAX)≠MAX(SUM))한 단일 시리즈로 표출한다. 각 분 값은 "
                    + "GOOD 품질 PWI 태그의 COALESCE(corr_val, raw_val) 합산이며(부분합 허용), BAD/UNCERTAIN·결측은 "
                    + "제외한다(PWI 는 Hold Last Value 미적용). PWI 보유·데이터 0 설비도 빈 points 시리즈로 포함하며, "
                    + "계측기·PWI 태그 부재 시 series 빈 배열을 반환한다. 조회기간 상한 31일.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (기간 결측, from > to, 31일 초과)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "리소스 없음 (미존재·비활성 시설)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/instrument-power-trend")
    public ResponseEntity<CommonResponseDto<FacilityInstrumentPowerTrendDto>> findInstrumentPowerTrend(
            @PathVariable String facilityId,
            @ModelAttribute FacilityInstrumentPowerTrendSearchDto search) {
        return getResponseEntity(
                facilityInstrumentPowerTrendService.findPowerTrend(facilityId, search));
    }
}
