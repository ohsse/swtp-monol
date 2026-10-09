package com.mo.swtp.facility.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.facility.dto.FacilityInstrumentEnergyUsageDto;
import com.mo.swtp.facility.dto.FacilityInstrumentEnergyUsageSearchDto;
import com.mo.swtp.facility.service.FacilityInstrumentEnergyUsageService;
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
 * 설비별 사용량 5·6번섹션 — 설비별 누적 전력량·분포율 조회 API 컨트롤러.
 *
 * <p>2번섹션에서 선택한 시설({@code facilityId})과 1번섹션 파라미터(시작/종료일자)를 받아, 그 시설을 루트로 하는
 * 재귀 하위 트리 전체의 설비별 누적 전력량(kWh, 5번섹션)과 분포율(%, 6번섹션)을 반환하는 읽기 전용 엔드포인트
 * (설비별사용량-5,6번섹션 PLAN1 §구현 방향 5).</p>
 *
 * <p>{@link FacilityController}(시설 CRUD + 2·3번섹션 조회) 와 같은 {@code /api/facility} 기준 경로를 사용하나,
 * SRP·{@code @Tag} 목적 분리를 위해 신규 클래스로 분리한다. 리터럴 세그먼트
 * ({@code /{facilityId}/instrument-energy-usage})는 {@code /{facilityId}} GET 보다 Spring PathPattern 특이도가
 * 높아 우선 매칭되므로 ambiguous-mapping 이 발생하지 않는다.</p>
 */
@Tag(name = "15. 설비별 사용량")
@RestController
@RequestMapping("/api/facility")
@RequiredArgsConstructor
public class FacilityInstrumentEnergyUsageController extends CommonController {

    private final FacilityInstrumentEnergyUsageService facilityInstrumentEnergyUsageService;

    @Operation(summary = "설비별 누적 전력량·분포율 조회 (5·6번섹션)",
            description = "2번섹션에서 선택한 시설을 루트로 parent_facility_id self-FK 를 재귀 탐색(하위 시설 전체)한 "
                    + "뒤, 그 시설들의 활성 계측기 중 PWQ(적산전력량) 태그 보유 계측기별 조회기간 누적 전력량(kWh, "
                    + "5번섹션)과 분포율(%, 6번섹션)을 반환한다. 분포율 = [설비 전력량 / 전체 설비 전력량] × 100 "
                    + "(소수 첫째자리 반올림, 항목별 독립 반올림이라 합이 정확히 100.0 이 아닐 수 있음). 각 설비 전력량은 "
                    + "PWQ 적산값의 GOOD 품질 raw_val 일 버킷 차분(MAX-MIN) 합산이며(corr_val 미사용), 음수 차분"
                    + "(적산 리셋·롤오버) 버킷은 생략한다. PWQ 보유·전력량 0 설비도 0kWh·0% 로 포함하며, 계측기·PWQ "
                    + "태그 부재 시 items 빈 배열·totalElceg 0 을 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (기간 결측, from > to, 396일 초과)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "리소스 없음 (미존재·비활성 시설)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/instrument-energy-usage")
    public ResponseEntity<CommonResponseDto<FacilityInstrumentEnergyUsageDto>> findInstrumentEnergyUsage(
            @PathVariable String facilityId,
            @ModelAttribute FacilityInstrumentEnergyUsageSearchDto search) {
        return getResponseEntity(
                facilityInstrumentEnergyUsageService.findInstrumentEnergyUsage(facilityId, search));
    }
}
