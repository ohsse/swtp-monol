package com.mo.swtp.opt.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.opt.dto.PeakEnergyTrendDto;
import com.mo.swtp.opt.dto.PeakPowerAnalysisDto;
import com.mo.swtp.opt.dto.PumpEnergyPredictionDto;
import com.mo.swtp.opt.service.PeakEnergyTrendService;
import com.mo.swtp.opt.service.PeakPowerAnalysisService;
import com.mo.swtp.opt.service.PumpEnergyPredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 전력피크분석 2·3·4번섹션 공용 조회 REST API 컨트롤러.
 *
 * <p>3 엔드포인트:</p>
 * <ul>
 *   <li>{@code GET /api/opt/peak-power-analysis} — 5지표 (총순시전력·송수펌프 순시전력·목표피크전력·
 *       요금적용전력피크·전력피크예상시간) 단일 조회. 2번섹션 패널 4지표 + 3번섹션 주요내역 송수펌프
 *       순시전력을 단일 응답으로 표출한다 (전력피크분석-3번섹션, 2026-06-05 — 엔드포인트 확장)</li>
 *   <li>{@code GET /api/opt/peak-power-analysis/pump-energy-prediction} — 선택 시설 펌프 전력량 예측
 *       시계열 조회. 시설 보유 활성 송수펌프들의 PWQ(적산전력량) 예측값을 현재 시 ~ +24시간 1시간 버킷
 *       단위로 합산한 단일 시계열을 표출한다 (전력피크분석-4번섹션, 2026-06-05 — 엔드포인트 확장)</li>
 *   <li>{@code GET /api/opt/peak-power-analysis/energy-trend} — 전력량 추이(발생·예측) 조회. 현재 시각 기준
 *       ±12시간(총 24시간) 윈도우의 시스템 전역 PWQ(적산전력량) 발생·예측 시계열 + 요금적용전력피크·목표피크
 *       스칼라를 단일 응답으로 표출한다 (전력피크분석-5번섹션, 2026-06-05 — 엔드포인트 확장)</li>
 * </ul>
 *
 * <p>읽기 전용 집계 — 단순 GET 폴링 방식 (목표값 변경 실시간 전파는 1번섹션 SSE
 * {@code PeakTargetSseController} 가 담당, 본 섹션은 SSE 미적용 — 사용자 결정 2).
 * 1번섹션 목표값 마스터 ({@code PeakTargetController}, {@code @Tag 13.}) 와 의미 분리하여 별도 컨트롤러로 둔다.</p>
 */
@Tag(name = "14. 전력피크 분석")
@RestController
@RequestMapping("/api/opt/peak-power-analysis")
@RequiredArgsConstructor
public class PeakPowerAnalysisController extends CommonController {

    private final PeakPowerAnalysisService peakPowerAnalysisService;
    private final PumpEnergyPredictionService pumpEnergyPredictionService;
    private final PeakEnergyTrendService peakEnergyTrendService;

    @Operation(summary = "전력피크분석 2·3번섹션 5지표 조회",
               description = "총순시전력·송수펌프 순시전력·목표피크전력·요금적용전력피크·전력피크예상시간 5지표를 단일 응답으로 반환한다. "
                       + "총순시전력은 전체 PWI 태그 최신 GOOD 합산, 송수펌프 순시전력은 펌프(equip_type_cd='PUMP') 매핑 PWI "
                       + "최신 GOOD 합산(On/Off 무관, 총순시전력의 부분집합), 요금적용전력피크는 최근 12개월 분단위 합산의 MAX, "
                       + "전력피크예상시간은 예측 합이 목표 초과하는 최근접 미래 시각(없으면 null).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류 (목표값 시드 미초기화 포함)")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<PeakPowerAnalysisDto>> getPeakPowerAnalysis() {
        return getResponseEntity(peakPowerAnalysisService.getPeakPowerAnalysis());
    }

    @Operation(summary = "전력피크분석 4번섹션 시설 펌프 전력량 예측 시계열 조회",
               description = "선택 시설이 보유한 활성 송수펌프(equip_type_cd='PUMP', use_yn='Y') 들의 적산전력량(PWQ) 예측값을 "
                       + "현재 시(時)부터 24시간까지 1시간 버킷 단위로 합산한 단일 시계열을 반환한다. 각 버킷 전력량은 펌프별 예측 "
                       + "적산값의 MAX(predc_val)-MIN(predc_val) 차분 후 시설 합산이며, 현재 시 버킷은 부분 집계, 음수 차분(적산 "
                       + "리셋) 버킷은 제외한다. 펌프·PWQ 태그·예측 데이터 부재 시 빈 시계열(points=[]) 을 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (facilityId 누락)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (미존재 또는 비활성)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/pump-energy-prediction")
    public ResponseEntity<CommonResponseDto<PumpEnergyPredictionDto>> getPumpEnergyPrediction(
            @RequestParam String facilityId) {
        return getResponseEntity(pumpEnergyPredictionService.getPumpEnergyPrediction(facilityId));
    }

    @Operation(summary = "전력피크분석 5번섹션 전력량 추이(발생·예측) 조회",
               description = "현재 시각 기준 ±12시간(총 24시간) 윈도우의 시스템 전역 적산전력량(PWQ) 추세를 단일 응답으로 반환한다. "
                       + "기준 시각 base=date_trunc('hour', now) 분기로 발생 전력량은 [base-12h, base) 실측(rawdata_1m_h) PWQ "
                       + "1시간 버킷 차분 후 전역 합산, 예측 전력량은 [base, base+12h) 예측(predc_1m_h) PWQ 1시간 버킷 차분 후 전역 "
                       + "합산이며 현재 시 버킷은 부분 집계다. 각 버킷은 태그별 MAX-MIN 차분 후 합산이고 음수 차분(적산 리셋) 버킷은 "
                       + "제외한다. 요금적용전력피크·목표피크는 차트 기준선 스칼라(kW, 시계열은 kWh — 단위 혼재). 태그·데이터 부재 시 "
                       + "빈 시계열(points=[]) + 스칼라 0 을 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류 (목표값 시드 미초기화 포함)")
    })
    @GetMapping("/energy-trend")
    public ResponseEntity<CommonResponseDto<PeakEnergyTrendDto>> getEnergyTrend() {
        return getResponseEntity(peakEnergyTrendService.getEnergyTrend());
    }
}
