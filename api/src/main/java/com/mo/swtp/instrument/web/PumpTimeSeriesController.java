package com.mo.swtp.instrument.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.instrument.dto.PumpFrequencyTimeSeriesDto;
import com.mo.swtp.instrument.dto.PumpPowerTimeSeriesDto;
import com.mo.swtp.instrument.dto.PumpTimeSeriesSearchDto;
import com.mo.swtp.instrument.service.PumpFrequencyTimeSeriesService;
import com.mo.swtp.instrument.service.PumpPowerTimeSeriesService;
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
 * 송수펌프 가동이력 3번섹션 — 전력량/주파수 시계열 차트 조회 API 컨트롤러.
 *
 * <p>1번섹션 파라미터(조회 단위 + from~to 날짜)를 받아 두 개의 펌프별 시계열을 반환하는 읽기 전용 엔드포인트.
 * 전력량 차트(전체 활성 펌프)와 주파수 차트(인버터 펌프 전용)가 독립 호출되도록 엔드포인트를 2개로 분리한다
 * (송수펌프가동이력_3번섹션 PLAN1 §구현 방향 7).</p>
 *
 * <p>계측기 CRUD ({@link InstrumentController}) · 2번섹션 ({@link PumpOperationRateController}) 과 같은
 * {@code /api/instrument} 기준 경로를 사용하나, 리터럴 세그먼트({@code /pump-power-timeseries} ·
 * {@code /pump-frequency-timeseries}) 가 {@code /{instrumentId}} path-variable 보다 Spring PathPattern
 * 특이도가 높아 우선 매칭된다 (ambiguous-mapping 미발생 — 2번섹션 {@code /pump-operation-rate} 선례).
 * 2번섹션 컨트롤러를 확장하지 않고 신규 클래스로 분리한다 ({@code @Tag} 문자열만 재사용).</p>
 *
 * <p>송수펌프가동이력_3번섹션 ANALYZE1·PLAN1 (2026-06-02) 도입.</p>
 */
@Tag(name = "11. 송수펌프 가동이력")
@RestController
@RequestMapping("/api/instrument")
@RequiredArgsConstructor
public class PumpTimeSeriesController extends CommonController {

    private final PumpPowerTimeSeriesService powerService;
    private final PumpFrequencyTimeSeriesService frequencyService;

    @Operation(summary = "송수펌프 전력량 시계열 조회 (3번섹션 차트1)",
            description = "전체 활성 송수펌프(use_yn=Y)의 조회기간 전력량(kWh)을 펌프별 계열로 반환한다. "
                    + "x축 버킷은 조회 단위(시/일/월/년)에 따라 변동한다. 각 버킷 전력량은 PWQ(적산전력량) 태그의 "
                    + "GOOD 품질 raw_val 차분(MAX-MIN)으로 산정하며(corr_val 미사용), 음수 차분(적산 리셋·롤오버) "
                    + "버킷은 생략한다. 데이터 없는 버킷·PWQ 태그 부재 펌프는 points 빈 배열.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (조회 단위·기간 결측 또는 from > to)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "리소스 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/pump-power-timeseries")
    public ResponseEntity<CommonResponseDto<List<PumpPowerTimeSeriesDto>>> findPumpPowerTimeSeries(
            @ModelAttribute PumpTimeSeriesSearchDto search) {
        return getResponseEntity(powerService.findPumpPowerTimeSeries(search));
    }

    @Operation(summary = "송수펌프 주파수 시계열 조회 (3번섹션 차트2)",
            description = "활성 인버터 송수펌프(drive_type_cd=INVERTER_DRIVE)의 조회기간 가변 주파수(Hz)를 펌프별 "
                    + "계열로 반환한다. 정격 펌프(RATED_DRIVE)는 주파수가 고정이라 제외된다. x축 버킷은 조회 단위 "
                    + "(시/일/월/년)에 따라 변동한다. 각 버킷 주파수는 FQI 태그의 GOOD 품질 AVG(COALESCE(corr_val,raw_val))로 "
                    + "산정한다. 데이터 없는 버킷·FQI 태그 부재 펌프는 points 빈 배열.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (조회 단위·기간 결측 또는 from > to)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "리소스 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/pump-frequency-timeseries")
    public ResponseEntity<CommonResponseDto<List<PumpFrequencyTimeSeriesDto>>> findPumpFrequencyTimeSeries(
            @ModelAttribute PumpTimeSeriesSearchDto search) {
        return getResponseEntity(frequencyService.findPumpFrequencyTimeSeries(search));
    }
}
