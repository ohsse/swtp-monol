package com.mo.swtp.facility.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.facility.dto.AcfbDto;
import com.mo.swtp.facility.dto.ChmbDto;
import com.mo.swtp.facility.dto.DewbDto;
import com.mo.swtp.facility.dto.DwtDto;
import com.mo.swtp.facility.dto.DwtGroupStateDto;
import com.mo.swtp.facility.dto.FacilityDto;
import com.mo.swtp.facility.domain.enumtype.FacilityDownstreamDataType;
import com.mo.swtp.facility.domain.enumtype.FacilityOperatingStatusCompareType;
import com.mo.swtp.facility.dto.FacilityDailyTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityDownstreamLevelDto;
import com.mo.swtp.facility.dto.FacilityDownstreamMeasureDto;
import com.mo.swtp.facility.dto.FacilityDownstreamTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityEnergyTrendDto;
import com.mo.swtp.facility.dto.FacilityEnergyTrendSearchDto;
import com.mo.swtp.facility.dto.FacilityEnergyUsageDto;
import com.mo.swtp.facility.dto.FacilityEnergyUsageSearchDto;
import com.mo.swtp.facility.dto.FacilityOperatingStatusDto;
import com.mo.swtp.facility.dto.FacilityOperatingStatusTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityOutflowTimeSeriesDto;
import com.mo.swtp.facility.dto.FacilityPowerInstrumentDto;
import com.mo.swtp.facility.dto.FacilityPredcOperatingStatusDto;
import com.mo.swtp.facility.dto.FacilityPredictionDto;
import com.mo.swtp.facility.dto.FacilitySearchDto;
import com.mo.swtp.facility.dto.FacilityStateDto;
import com.mo.swtp.facility.dto.FacilityUpsertDto;
import com.mo.swtp.facility.dto.FltbDto;
import com.mo.swtp.facility.dto.PointDto;
import com.mo.swtp.facility.dto.PozbDto;
import com.mo.swtp.facility.dto.PrsfDto;
import com.mo.swtp.facility.dto.PumpSummaryDto;
import com.mo.swtp.facility.dto.PwtfDto;
import com.mo.swtp.facility.dto.RsvDto;
import com.mo.swtp.facility.dto.SolarDto;
import com.mo.swtp.facility.dto.WtbldDto;
import com.mo.swtp.facility.service.DwtStateService;
import com.mo.swtp.facility.service.FacilityDailyTimeSeriesService;
import com.mo.swtp.facility.service.FacilityDownstreamTimeSeriesService;
import com.mo.swtp.facility.service.FacilityEnergyTrendService;
import com.mo.swtp.facility.service.FacilityEnergyUsageService;
import com.mo.swtp.facility.service.FacilityOperatingStatusService;
import com.mo.swtp.facility.service.FacilityOperatingStatusTimeSeriesService;
import com.mo.swtp.facility.service.FacilityOutflowTimeSeriesService;
import com.mo.swtp.facility.service.FacilityPowerInstrumentService;
import com.mo.swtp.facility.service.FacilityPredcOperatingStatusService;
import com.mo.swtp.facility.service.FacilityPredictionService;
import com.mo.swtp.facility.service.FacilityService;
import com.mo.swtp.facility.service.FacilityStateService;
import com.mo.swtp.facility.service.PumpSummaryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 시설물 관리 API 컨트롤러 — 자식 11종 (PWTF/DWT/RSV/PRSF/WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR) 통합 CRUD.
 *
 * <p>Jackson 다형성 ({@link FacilityUpsertDto} {@code @JsonTypeInfo} + {@code @JsonSubTypes}) 으로
 * 단일 엔드포인트 (POST·PUT) 가 자식 11종 요청 본문을 자동 분기한다. POINT 자식은 SCADA 도메인 자동
 * 생성으로 등록·수정 API 대상 외 (조회 응답은 POINT 포함 12종). 시설_도메인_확장 ANALYZE1 (2026-06-08)
 * 운영시설 7종 추가, 시설물관리기능 ANALYZE1 (2026-05-11) POINT 등록 제외 결정.</p>
 *
 * <p>등록·수정·삭제는 향후 ADMIN 전용 권한 분리 사이클에서 검토 (현재 인증 사용자 전체 허용).</p>
 */
@Tag(name = "06. 시설물 관리")
@RestController
@RequestMapping("/api/facility")
@RequiredArgsConstructor
public class FacilityController extends CommonController {

    private final FacilityService facilityService;
    private final FacilityStateService facilityStateService;
    private final FacilityOperatingStatusService facilityOperatingStatusService;
    private final FacilityOperatingStatusTimeSeriesService facilityOperatingStatusTimeSeriesService;
    private final FacilityPredcOperatingStatusService facilityPredcOperatingStatusService;
    private final FacilityDailyTimeSeriesService facilityDailyTimeSeriesService;
    private final FacilityOutflowTimeSeriesService facilityOutflowTimeSeriesService;
    private final FacilityDownstreamTimeSeriesService facilityDownstreamTimeSeriesService;
    private final FacilityPredictionService facilityPredictionService;
    private final DwtStateService dwtStateService;
    private final PumpSummaryService pumpSummaryService;
    private final FacilityEnergyUsageService facilityEnergyUsageService;
    private final FacilityEnergyTrendService facilityEnergyTrendService;
    private final FacilityPowerInstrumentService facilityPowerInstrumentService;

    @Operation(summary = "시설 목록 조회",
            description = "facilityTypeCd / facilityGroupCd / useYn / hasPump 필터로 시설 목록을 조회한다. 네 필터 모두 NULL 허용 (NULL 시 전체 반환). "
                    + "facilityGroupCd 는 시설 그룹 단위 필터로, facility_type_cd IN (그룹 소속 유형) 으로 전개된다 — 예: OPERATION 은 운영시설 8종(PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR) IN 조회 (설비별 사용량 2번 섹션 운영시설 목록 용). facilityTypeCd 와 동시 지정 시 AND 교집합. "
                    + "hasPump=true 면 활성 펌프(instrument_m.equip_type_cd=PUMP AND use_yn=Y) 1대 이상 보유 시설만 응답한다 (송수펌프제어 화면 1번 섹션 용). "
                    + "정렬: useYn DESC (활성 우선) → dispOrd ASC → facilityNm ASC.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 — 응답 배열 element 는 facilityTypeCd discriminator 로 자식 DTO 결정",
                    content = @Content(array = @ArraySchema(schema = @Schema(
                            oneOf = {DwtDto.class, PwtfDto.class, RsvDto.class, PrsfDto.class, PointDto.class,
                                    WtbldDto.class, ChmbDto.class, AcfbDto.class, PozbDto.class,
                                    FltbDto.class, DewbDto.class, SolarDto.class},
                            discriminatorProperty = "facilityTypeCd")))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<List<FacilityDto>>> findAllFacilities(
            @ModelAttribute FacilitySearchDto searchDto) {
        return getResponseEntity(facilityService.findAllFacilities(searchDto));
    }

    @Operation(summary = "시설 단건 조회",
            description = "facilityId 로 시설 단건을 조회한다. DWT 자식은 minReqPrsr 값을 포함한다.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 — facilityTypeCd discriminator 로 자식 DTO 결정",
                    content = @Content(schema = @Schema(
                            oneOf = {DwtDto.class, PwtfDto.class, RsvDto.class, PrsfDto.class, PointDto.class,
                                    WtbldDto.class, ChmbDto.class, AcfbDto.class, PozbDto.class,
                                    FltbDto.class, DewbDto.class, SolarDto.class},
                            discriminatorProperty = "facilityTypeCd"))),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}")
    public ResponseEntity<CommonResponseDto<FacilityDto>> findFacility(@PathVariable String facilityId) {
        return getResponseEntity(facilityService.findFacilityDto(facilityId));
    }

    @Operation(summary = "시설 실시간 상태 조회",
            description = "송수펌프제어분석 3번 섹션 — 활성 시설의 유량계(FLWMTR) FRI/PRI 최신값 + "
                    + "펌프(PUMP) OPS 가동상태 + 정적 oprtngType 을 단건 응답으로 반환한다. "
                    + "rawdata_1m_h DISTINCT ON + acq_dtm >= NOW() - INTERVAL '1 hour' 파티션 프루닝. "
                    + "1시간 윈도우 내 측정값이 없는 태그는 null 반환.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/state")
    public ResponseEntity<CommonResponseDto<FacilityStateDto>> findFacilityState(
            @PathVariable String facilityId) {
        return getResponseEntity(facilityStateService.findFacilityState(facilityId));
    }

    @Operation(summary = "활성 시설의 운영 현황 조회 (운전현황분석 4번 섹션)",
            description = "운전현황분석 4번 섹션 — 활성 시설의 PUMP 자식 인스트루먼트 중 OPS=On (GOOD+1.0) 펌프 "
                    + "이름 목록, 해당 펌프들의 PWI 합산(kW), 시설 직속 FLWMTR 의 FRI 와의 전력원단위(kWh/m³), "
                    + "사용된 모든 태그의 max(acq_dtm) 을 단일 응답으로 반환한다. "
                    + "지원 시설 종류: PWTF · DWT · PRSF (RSV·POINT 는 400 거부). "
                    + "UNCERTAIN OPS 는 On 제외 (ot-integration.md §3 OPS BAD 즉시 격상 보수적 확장). "
                    + "UNCERTAIN PWI 는 합산 전액 제외 (가중치 0.5 미적용). "
                    + "전력원단위 분자/분모 0/NULL/BAD/부재 4 케이스 모두 null 반환 (0 나누기 방어).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description =
                    "잘못된 요청 (UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류 거부)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/operating-status")
    public ResponseEntity<CommonResponseDto<FacilityOperatingStatusDto>> findFacilityOperatingStatus(
            @PathVariable String facilityId) {
        return getResponseEntity(facilityOperatingStatusService.findFacilityOperatingStatus(facilityId));
    }

    @Operation(summary = "활성 시설의 예측 운영 현황 조회 (운전현황분석 9번 섹션)",
            description = "운전현황분석 9번 섹션 — 활성 시설의 PUMP 자식 인스트루먼트 중 OPS 예측이 On "
                    + "(predc_val = 1.0) 인 펌프 이름 목록, 해당 펌프들의 PWI 예측값 합산(kW), 시설 직속 FLWMTR 의 "
                    + "FRI 예측값과의 예측 전력원단위(kWh/m³), 사용된 모든 예측 태그의 max(predc_dtm) 을 단일 응답으로 "
                    + "반환한다. 4번 섹션 동형 구조이나 데이터 소스가 predc_1m_h (AI 예측 시계열) 로 변경된 예측값 버전. "
                    + "지원 시설 종류: PWTF · DWT · PRSF (RSV·POINT 는 400 거부, 4번 섹션 ErrorCode 재사용). "
                    + "predc_1m_h 는 quality_cd·corr_val 컬럼 부재 — SCADA QUALITY 분기 + Hold Last Value 분기 모두 미적용. "
                    + "predc_val IS NULL (결측·신뢰도 불명) 과 predc_val == 0.0 (예측 Off 확신) 두 케이스 모두 onPumpNms 에서 제외. "
                    + "예측 결측 펌프는 totalElpwrAmt 합산에서 제외 (해당 펌프만, 다른 펌프 합산 유지). "
                    + "FRI 예측 결측 또는 0 시 elpwrUnitQty = null (0 나누기 방어). "
                    + "predc_1m_h DISTINCT ON + predc_dtm >= NOW() - INTERVAL '1 hour' 파티션 프루닝.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description =
                    "잘못된 요청 (UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류 거부)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/operating-status/prediction")
    public ResponseEntity<CommonResponseDto<FacilityPredcOperatingStatusDto>> findFacilityPredcOperatingStatus(
            @PathVariable String facilityId) {
        return getResponseEntity(
                facilityPredcOperatingStatusService.findFacilityPredcOperatingStatus(facilityId));
    }

    @Operation(summary = "활성 시설의 운영 현황 시계열 조회 (운전현황분석 5번 섹션)",
            description = "운전현황분석 5번 섹션 — 활성 시설의 금일(00:00 ~ 현재) 실측과 "
                    + "비교 기간(YESTERDAY 또는 LAST_WEEK) 실측을 시간:분(HH:mm) 키로 머지한 "
                    + "1440 고정 단일 series 로 응답한다 (옵션 T — frontend 머지 부담 0). "
                    + "각 슬롯은 todayElpwrUnitQty(금일 실측 전력원단위 kWh/m³), "
                    + "comparisonElpwrUnitQty(비교일 실측 전력원단위 kWh/m³), "
                    + "todayOnPumpCnt(금일 OPS=GOOD+1.0 운영 펌프 대수) 3 시리즈를 표현한다. "
                    + "series 는 00:00~23:59 의 1440 슬롯 전부 포함하며, 결측 시점은 해당 컬럼만 null 채움. "
                    + "baseDate(금일)·comparisonDate(비교일 실제 날짜) 메타 동봉. "
                    + "지원 시설 종류: PWTF · DWT · PRSF (RSV·POINT 는 400 거부). "
                    + "전력원단위 산정식은 4번 섹션과 동일 — sum(On 펌프 PWI GOOD) / 시설 직속 FLWMTR FRI GOOD. "
                    + "rawdata_1m_h Querydsl 범위 조회 + acq_dtm 범위 조건으로 월 RANGE 파티션 프루닝 강제. "
                    + "지난달 평균(LAST_MONTH_AVG) 옵션은 별도 데이터 집계 사이클에서 처리.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description =
                    "잘못된 요청 (UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류 거부, "
                            + "또는 compareType 누락·잘못된 값)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/operating-status/timeseries")
    public ResponseEntity<CommonResponseDto<FacilityOperatingStatusTimeSeriesDto>> findFacilityOperatingStatusTimeSeries(
            @Parameter(description = "조회 대상 시설 ID", example = "fa-xxx-xxx")
            @PathVariable String facilityId,
            @Parameter(description = "비교 기간 — YESTERDAY 또는 LAST_WEEK",
                    schema = @Schema(implementation = FacilityOperatingStatusCompareType.class))
            @RequestParam FacilityOperatingStatusCompareType compareType) {
        return getResponseEntity(facilityOperatingStatusTimeSeriesService
                .findFacilityOperatingStatusTimeSeries(facilityId, compareType));
    }

    @Operation(summary = "활성 시설의 금일 하루치 계측+예측 시계열 조회 (운전현황분석 10번 섹션)",
            description = "운전현황분석 10번 섹션 — 활성 시설의 금일 하루치(00:00 ~ 익일 00:00 전) 시계열을 "
                    + "1분 단위로 응답한다. 계측(actual) 은 자정부터 현재시간까지 슬롯에 존재, 예측(predc) 은 "
                    + "자정부터 익일 자정전까지 슬롯에 존재 — 두 범위를 합본하여 단일 시계열에 묶는다. "
                    + "각 1분 시점은 actual 3 항목 (actualElpwrAmt·actualFlwrt·actualUnitQty) + "
                    + "predc 4 항목 (predcElpwrAmt·predcFlwrt·predcUnitQty·predcPumpOnCnt) = 7 항목으로 표현된다. "
                    + "지원 시설 종류: PWTF · DWT · PRSF (RSV·POINT 는 400 거부). "
                    + "전력원단위 산정식 — actual: sum(On 펌프 PWI GOOD) / 시설 직속 FLWMTR FRI GOOD (5번 동형) · "
                    + "predc: sum(예측 On 펌프 PWI) / 시설 직속 FLWMTR FRI 예측값 (9번 동형). "
                    + "predcPumpOnCnt — predc_val = 1.0 카운트 합산 (예측 가동 펌프 대수). "
                    + "rawdata_1m_h + predc_1m_h Querydsl 범위 조회 + 시점 범위 조건으로 월 RANGE 파티션 프루닝 강제. "
                    + "양쪽 부재(actual·predc 모두 결측) 슬롯은 응답에서 생략. "
                    + "actual NULL — 미도래 시점 또는 결측 · predc NULL — 예측 미수행 슬롯 (의미 분리).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description =
                    "잘못된 요청 (UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류 거부)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/operating-status/daily-time-series")
    public ResponseEntity<CommonResponseDto<FacilityDailyTimeSeriesDto>> findFacilityDailyTimeSeries(
            @Parameter(description = "조회 대상 시설 ID", example = "fa-xxx-xxx")
            @PathVariable String facilityId) {
        return getResponseEntity(
                facilityDailyTimeSeriesService.findFacilityDailyTimeSeries(facilityId));
    }

    @Operation(summary = "활성 시설의 유출 유량·압력 + 펌프 가동상태 계측+예측 시계열 조회 (운전현황분석 7번 섹션)",
            description = "운전현황분석 7번 섹션 — 활성 시설의 금일(00:00 ~ 현재시간) 유출(송수) 유량·압력 라인과 "
                    + "펌프 가동상태 막대를 1분 단위 계측+예측 시계열로 응답한다. 계측·예측 모두 동일 구간(금일 00:00 ~ 현재시간). "
                    + "라인(linePoints) — 시설 직속 FLWMTR(첫 매치 고정) 의 FRI(유량)·PRI(압력) 계측값(GOOD)·예측값. "
                    + "막대(pumpSeries) — 시설 소속 모든 PUMP 별 OPS 가동상태 계측·예측 (펌프 1대 = 1 트랙, OPS 부재 펌프도 빈 트랙 포함). "
                    + "OPS 가동상태는 계측·예측 원본값 tri-state — 1=가동 / 0=정지 / null=불명(결측·BAD·UNCERTAIN·예측 미수행). "
                    + "BAD/UNCERTAIN 을 정지로 표시하지 않기 위해 불명으로 분리 (10번 섹션 2-state 와 의도 분리, ot-integration.md §3 정합). "
                    + "지원 시설 종류: PWTF · DWT · PRSF (RSV·POINT 는 400 거부). "
                    + "rawdata_1m_h + predc_1m_h Querydsl 범위 조회 + 시점 범위 조건으로 월 RANGE 파티션 프루닝 강제. "
                    + "라인 4값(계측·예측 유량·압력) 모두 결측 슬롯·펌프 막대 양쪽 불명 슬롯은 응답에서 생략.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description =
                    "잘못된 요청 (UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS — RSV/POINT 시설 종류 거부)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/operating-status/outflow-time-series")
    public ResponseEntity<CommonResponseDto<FacilityOutflowTimeSeriesDto>> findFacilityOutflowTimeSeries(
            @Parameter(description = "조회 대상 시설 ID", example = "fa-xxx-xxx")
            @PathVariable String facilityId) {
        return getResponseEntity(
                facilityOutflowTimeSeriesService.findFacilityOutflowTimeSeries(facilityId));
    }

    @Operation(summary = "활성 루트 시설의 재귀 하위 수요량/관압/수위 계측+예측 시계열 조회 (운전현황분석 8번 섹션)",
            description = "운전현황분석 8번 섹션 — 12번 섹션 활성 시설(루트)을 상위로 바라보고, 그 재귀 하위 중 "
                    + "배수지(DWT)를 자식으로 가진 정수지(PWTF)·분기점(POINT)을 표출 대상으로 삼아 금일(00:00 ~ 현재시간) "
                    + "계측+예측 시계열을 1분 단위로 응답한다. 단일 엔드포인트가 dataType (수요량/관압/수위 탭)으로 분기한다 — "
                    + "DEMAND/PRESSURE 는 표출대상 시설당 1 시리즈(유출 FLWMTR io_cd ∈ {OUTPUT,BIDIR} 첫 매치의 FRI/PRI, "
                    + "유량·압력 모두 유량계 유출값 기준), LEVEL 은 자식 DWT 의 수위계(LVMTR)당 1 시리즈(LEI, io_cd 무필터). "
                    + "응답은 dataType discriminator 로 Measure(DEMAND·PRESSURE)/Level(LEVEL) 자식 스키마로 직렬화된다. "
                    + "각 슬롯은 계측값(GOOD, corr_val 우선)·예측값(predc_val)·대비율(예측/계측×100, 소수1자리)을 담는다. "
                    + "계측값 NULL·0 또는 예측값 NULL 시 대비율 NULL (0 나누기·무의미 비율 방어). "
                    + "계측·예측 양쪽 부재 슬롯은 응답에서 생략. 유출 FLWMTR 다중 등록 시 첫 매치 + multipleOutletFlwmtrDetected=true. "
                    + "재귀 하위 도출은 parent_facility_id self-FK 앱 레벨 BFS(visited-set 순환 방어). 루트 inclusive(루트가 "
                    + "PWTF·POINT 이고 DWT 자식 보유 시 표출대상 포함). 루트 시설 종류 제한 없음 — 미존재·비활성만 404. "
                    + "rawdata_1m_h + predc_1m_h IN 절 범위 조회로 월 RANGE 파티션 프루닝 강제. "
                    + "표출대상 0건 시 빈 series(200).")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 — dataType discriminator 로 Measure(DEMAND·PRESSURE)/Level(LEVEL) 자식 스키마 결정",
                    content = @Content(schema = @Schema(
                            oneOf = {FacilityDownstreamMeasureDto.class, FacilityDownstreamLevelDto.class},
                            discriminatorProperty = "dataType"))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (dataType 누락·잘못된 값)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성 루트 시설)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/operating-status/downstream-time-series")
    public ResponseEntity<CommonResponseDto<FacilityDownstreamTimeSeriesDto>> findDownstreamTimeSeries(
            @Parameter(description = "활성 루트 시설 ID (12번 섹션 활성 시설)", example = "fa-xxx-xxx")
            @PathVariable String facilityId,
            @Parameter(description = "표출 데이터 종류 — DEMAND(수요량)/PRESSURE(관압)/LEVEL(수위)",
                    schema = @Schema(implementation = FacilityDownstreamDataType.class))
            @RequestParam FacilityDownstreamDataType dataType) {
        return getResponseEntity(
                facilityDownstreamTimeSeriesService.findDownstreamTimeSeries(facilityId, dataType));
    }

    @Operation(summary = "시설 예측 데이터 조회",
            description = "송수펌프제어분석 7번 섹션 — 활성 시설의 유량계(FLWMTR) FRI/PRI 예측값 + "
                    + "펌프(PUMP) OPS 예측 가동상태를 각 태그 현황 최신 계측시각+1시간 근접 예측행으로 반환한다. "
                    + "predc_1m_h latest_meas CTE + CROSS JOIN LATERAL 근접매칭 — 기본 윈도우 ±5분 "
                    + "(opt.prediction.match-window-minutes). 윈도우 내 예측행이 없는 태그는 null 반환. "
                    + "quality_cd 컬럼 부재 — 예측값에는 SCADA QUALITY 개념이 없음.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/prediction")
    public ResponseEntity<CommonResponseDto<FacilityPredictionDto>> findFacilityPrediction(
            @PathVariable String facilityId) {
        return getResponseEntity(facilityPredictionService.findFacilityPrediction(facilityId));
    }

    @Operation(summary = "부모 시설의 자식 배수지(DWT) 실시간 현황 조회",
            description = "송수펌프제어분석 4번 섹션 — 활성 부모 시설(예: 성주정수장)의 자식 DWT 들의 "
                    + "유입/유출 유량계(FRI·PRI) + 밸브(VOI 개도) + 수위계(LEI) 최신 측정값을 단일 응답으로 반환한다. "
                    + "유입/유출은 Tag.io_cd (INPUT/OUTPUT/BIDIR) 로 구분 — BIDIR 은 양쪽 후보. "
                    + "유입 또는 유출 FLWMTR 가 한 DWT 에 2건 이상 등록 시 첫 매치 + multipleIn/OutFlwmtrDetected = true. "
                    + "rawdata_1m_h DISTINCT ON + acq_dtm >= NOW() - INTERVAL '1 hour' 파티션 프루닝. "
                    + "1시간 윈도우 내 측정값이 없는 태그는 null 반환.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "부모 시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{parentFacilityId}/dwts/states")
    public ResponseEntity<CommonResponseDto<DwtGroupStateDto>> findDwtStates(
            @PathVariable String parentFacilityId) {
        return getResponseEntity(dwtStateService.findDwtStates(parentFacilityId));
    }

    @Operation(summary = "송수펌프 시설별 토출관압·운전중 펌프 대수 요약 조회",
            description = "송수펌프제어분석 6번 섹션 — 1번 섹션 시설목록(hasPump=true 활성 펌프 시설 전체)의 "
                    + "시설별 토출관압(FLWMTR 첫 매치의 PRI 최신값) + 운전중 펌프 대수(PUMP OPS 가동상태 ON)를 "
                    + "목록 응답한다. path variable 없음 — 1번 섹션에서 활성화한 시설과 무관하게 전체 시설의 "
                    + "최신값을 항상 유지한다. 시설 정렬: useYn DESC → dispOrd ASC → facilityNm ASC. "
                    + "rawdata_1m_h DISTINCT ON + acq_dtm >= NOW() - INTERVAL '1 hour' 파티션 프루닝. "
                    + "토출관압 FLWMTR/PRI 다중 등록 시 첫 매치 + multiplePrsrDetected=true. "
                    + "OPS qualityCd BAD/UNCERTAIN·결측·판정불가 펌프는 unknownPumpCnt 로 분리 (운전원 오인 방지).")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 — hasPump=true 시설 전체 요약 배열",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = PumpSummaryDto.class)))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/pump-summary")
    public ResponseEntity<CommonResponseDto<List<PumpSummaryDto>>> findPumpSummaries() {
        return getResponseEntity(pumpSummaryService.findPumpSummaries());
    }

    @Operation(summary = "운영시설 전력 사용량 조회 (시설별 사용량 2번 섹션)",
            description = "시설별 사용량 2번 섹션 — 운영시설(FacilityGroup.OPERATION 8종) 각각의 전력 사용량 4지표를 "
                    + "집계단위(inqUnit 시/일/월) · 검색기간(fromDt~toDt) 으로 조회한다. "
                    + "지표: 순시전력(elpwr, kW — 조회기간 마지막 분의 시설합 PWI) · "
                    + "전력량(elceg, kWh — PWQ 적산 버킷 차분 합) · "
                    + "최대전력(peakElpwr, kW — 집계단위 버킷별 시설합 PWI MAX 중 최댓값) · "
                    + "최대전력 일시(peakElpwrDtm — 발생 버킷 시작 시각, 집계단위 입도). "
                    + "전력 측정 대상은 운영시설 + 모든 재귀 하위 시설(parent_facility_id)의 모든 계측기 PWI/PWQ 태그 "
                    + "분(分)별 합산이며, 각 태그는 최근접 운영시설 조상으로 귀속된다(중복 합산 방지). "
                    + "데이터 부재(전 구간 BAD / 태그 없음) 시 4지표 모두 null (0kW 와 측정 없음 구분). "
                    + "분별 시설합은 같은 acq_dtm 에서 SUM(SUM(MAX)≠MAX(SUM)), GOOD 품질만 COALESCE(corr_val,raw_val) 합산. "
                    + "PWQ 전력량은 GOOD+raw_val 차분(Hold Last Value 미적용), 음수 차분(적산 리셋) 제외. "
                    + "rawdata_1m_h acq_dtm 범위 조건으로 월 RANGE 파티션 프루닝 강제. "
                    + "정렬: dispOrd ASC → facilityNm ASC. 집계단위 YEAR·조회기간 13개월 초과 시 400.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 — 운영시설별 전력 사용량 배열",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = FacilityEnergyUsageDto.class)))),
            @ApiResponse(responseCode = "400", description =
                    "잘못된 요청 (INVALID_SEARCH_PERIOD — 시작일/종료일 누락·역전, 집계단위 누락·YEAR, 기간 13개월 초과)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/energy-usage")
    public ResponseEntity<CommonResponseDto<List<FacilityEnergyUsageDto>>> findFacilityEnergyUsage(
            @ModelAttribute FacilityEnergyUsageSearchDto searchDto) {
        return getResponseEntity(facilityEnergyUsageService.findEnergyUsage(searchDto));
    }

    @Operation(summary = "운영시설 전력량 트렌드 조회 (시설별 사용량 5번 섹션)",
            description = "시설별 사용량 5번 섹션 — 운영시설(FacilityGroup.OPERATION 8종) 각각의 전력량(elceg, kWh)을 "
                    + "집계단위(inqUnit 시/일/월) · 검색기간(fromDt~toDt) 버킷별로 보존해 시계열(트렌드)로 조회한다. "
                    + "2번 섹션이 PWQ 적산 버킷 차분을 단일값으로 합치는 반면, 본 섹션은 같은 버킷 차분을 집계단위 버킷별로 "
                    + "보존해 시설별 시리즈(points)로 반환한다. "
                    + "전력 측정 대상은 운영시설 + 모든 재귀 하위 시설(parent_facility_id)의 모든 계측기 PWQ 태그이며, "
                    + "각 태그는 최근접 운영시설 조상으로 귀속된다(중복 합산 방지). "
                    + "각 버킷은 태그별 MAX(raw_val)-MIN(raw_val) 차분 후 시설 합산(GOOD 품질·Hold Last Value 미적용), "
                    + "음수 차분(적산 리셋) 제외. 데이터 없는 버킷은 생략(연속 시간축은 frontend 구성), "
                    + "측정 0건 시설은 points 빈 배열로 응답에 포함. baseDtm 오름차순 정렬. "
                    + "rawdata_1m_h acq_dtm 범위 조건으로 월 RANGE 파티션 프루닝 강제. "
                    + "시설 정렬: dispOrd ASC → facilityNm ASC. 집계단위 YEAR·조회기간 13개월 초과 시 400.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 — 운영시설별 전력량 시계열 배열 (시설별 시리즈)",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = FacilityEnergyTrendDto.class)))),
            @ApiResponse(responseCode = "400", description =
                    "잘못된 요청 (INVALID_SEARCH_PERIOD — 시작일/종료일 누락·역전, 집계단위 누락·YEAR, 기간 13개월 초과)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/energy-trend")
    public ResponseEntity<CommonResponseDto<List<FacilityEnergyTrendDto>>> findFacilityEnergyTrend(
            @ModelAttribute FacilityEnergyTrendSearchDto searchDto) {
        return getResponseEntity(facilityEnergyTrendService.findEnergyTrend(searchDto));
    }

    @Operation(summary = "전력 계측기 목록 조회 (시설별 사용량 3번 섹션)",
            description = "시설별 사용량 3번 섹션 — 2번 섹션에서 선택한 운영시설(facilityId)을 루트로 하는 재귀 하위 트리 "
                    + "전체(루트 inclusive)의 활성 계측기 중 전력관련 태그(PWI 순시전력 ∪ PWQ 적산전력량)를 1건 이상 "
                    + "보유한 계측기 목록을 반환한다. 5/6/7 섹션(설비별 통계·비율·순시전력 차트)의 입력 목록이다. "
                    + "재귀 하위 도출은 parent_facility_id self-FK 앱 레벨 BFS(visited-set 순환 방어, MAX_DEPTH=10)로 "
                    + "더 이상 하위 시설이 없을 때까지 탐색한다. 루트 시설 종류 제한 없음 — 미존재·비활성만 404. "
                    + "시설·계측기·태그 모두 use_yn=Y 활성만 집계. 계측기 1건당 소속 시설(facilityId·facilityNm)과 "
                    + "보유 전력태그 상세(tags — tagSrlNo·tagSeCd)를 함께 담으며, PWI·PWQ 동시 보유 계측기는 tags 2건. "
                    + "전력태그 보유 계측기 0건 시 빈 목록(200). 정렬: 시설 dispOrd ASC → 계측기 dispOrd ASC → 계측기명 ASC.")
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "성공 — 전력태그 보유 계측기 배열 (전력태그 0건 시 빈 배열)",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = FacilityPowerInstrumentDto.class)))),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음 (FACILITY_NOT_FOUND — 미존재 또는 비활성 루트 시설)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{facilityId}/power-instruments")
    public ResponseEntity<CommonResponseDto<List<FacilityPowerInstrumentDto>>> findFacilityPowerInstruments(
            @Parameter(description = "루트 시설 ID (2번 섹션에서 선택한 운영시설)", example = "fa-xxx-xxx")
            @PathVariable String facilityId) {
        return getResponseEntity(facilityPowerInstrumentService.findPowerInstruments(facilityId));
    }

    @Operation(summary = "시설 등록",
            description = "facilityTypeCd 필드로 자식 종류 (PWTF/DWT/RSV/PRSF/WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR) 결정. "
                    + "DWT 는 minReqPrsr 필수. 신규 운영시설 7종(WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR)은 자식 전용 필드 없이 공통 필드만 사용. "
                    + "POINT 는 SCADA 자동 생성으로 등록 대상 외. 요청 본문은 Jackson 다형성으로 자식 DTO 로 자동 역직렬화된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (INVALID_PARENT_FACILITY_ID·검증 실패)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_FACILITY_NM (시설명 중복)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<CommonResponseDto<String>> saveFacility(
            @Valid @RequestBody FacilityUpsertDto dto) {
        return getResponseEntity(facilityService.saveFacility(dto));
    }

    @Operation(summary = "시설 수정",
            description = "path 의 facilityId 와 request body 의 facilityTypeCd 가 일치해야 한다 "
                    + "(PWTF/DWT/RSV/PRSF/WTBLD/CHMB/ACFB/POZB/FLTB/DEWB/SOLAR). "
                    + "JPA dirty checking 으로 UPDATE 발행. DWT 는 minReqPrsr 함께 변경.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (FACILITY_TYPE_MISMATCH·INVALID_PARENT_FACILITY_ID)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음"),
            @ApiResponse(responseCode = "409", description = "DUPLICATE_FACILITY_NM (시설명 중복)"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/{facilityId}")
    public ResponseEntity<CommonResponseDto<Void>> updateFacility(
            @PathVariable String facilityId,
            @Valid @RequestBody FacilityUpsertDto dto) {
        facilityService.updateFacility(facilityId, dto);
        return getResponseEntity();
    }

    @Operation(summary = "시설 논리 삭제",
            description = "use_yn = N 으로 전환한다. 물리 삭제하지 않는 이유: instrument_m·ai_drvn_mod_p·"
                    + "rawdata_1m_h 가 facilityId 를 논리 참조하므로 (참조 보존 정책).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "시설 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{facilityId}")
    public ResponseEntity<CommonResponseDto<Void>> deactivateFacility(@PathVariable String facilityId) {
        facilityService.deactivateFacility(facilityId);
        return getResponseEntity();
    }
}
