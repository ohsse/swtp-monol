package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 시설 단위 금일 하루치 계측+예측 시계열 응답 DTO — 운전현황분석 10번 섹션.
 *
 * <p>활성 시설(useYn = Y, 지원 종류 PWTF/DWT/PRSF) 의 금일 하루치({@code 00:00 ~ 익일 00:00 전}) 시계열을
 * 1분 단위로 응답한다. 각 1분 시점은 계측값(actual) 7항목 + 예측값(predc) 4항목으로 표현되며,
 * 계측은 자정부터 현재시간까지·예측은 자정부터 익일 자정전까지 범위를 갖는다 (사용자 결정 — 10번 섹션 ANALYZE1).</p>
 *
 * <p>5번 섹션 {@link FacilityOperatingStatusTimeSeriesDto} (금일+비교기간 2개 시계열) 와 의도가 다르다 —
 * 본 사이클은 금일 단일 시계열 + actual·predc 합본 응답이며 별도 DTO 로 분리한다 (사용자 메모리 "사이클 간
 * 자산 자동 원용 금지" 정합).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 *
 * <p>전력원단위 산정식 — actual: {@code sum(On 펌프 PWI GOOD) / 시설 직속 FLWMTR FRI GOOD} (5번 섹션 동형) ·
 * predc: {@code sum(예측 On 펌프 PWI) / 시설 직속 FLWMTR FRI 예측값} (9번 섹션 동형). 1분 시점 단위로 적용
 * (10번 섹션 ANALYZE1).</p>
 *
 * <p>양쪽 부재(actual·predc 모두 결측) 슬롯은 응답에서 생략된다 — 14시 시점 기준 actual 은 자정~14:00 까지만,
 * predc 는 23:59:00 까지 존재하므로 시간대별로 actual NULL vs predc NULL 의미가 분리된다 (PLAN1 §시리즈 병합 정책).</p>
 */
@Getter
@Schema(description = "시설 단위 금일 하루치 계측+예측 시계열 — 운전현황분석 10번 섹션")
public class FacilityDailyTimeSeriesDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "정수지A")
    private String facilityNm;

    @ArraySchema(schema = @Schema(description = "금일 하루치 시계열 포인트 목록 (00:00 ~ 익일 00:00 전, 1분 간격)",
            implementation = DailyTimeSeriesPoint.class))
    private List<DailyTimeSeriesPoint> points;

    private FacilityDailyTimeSeriesDto() {
    }

    /**
     * 시설 금일 하루치 계측+예측 시계열 응답 DTO 정적 팩토리.
     *
     * @param facilityId 시설 ID
     * @param facilityNm 시설명
     * @param points     1분 시계열 포인트 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static FacilityDailyTimeSeriesDto of(
            String facilityId,
            String facilityNm,
            List<DailyTimeSeriesPoint> points) {
        FacilityDailyTimeSeriesDto dto = new FacilityDailyTimeSeriesDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.points = points;
        return dto;
    }

    /**
     * 1분 시계열 단일 포인트 — actual(계측) 3 항목 + predc(예측) 4 항목.
     *
     * <p>각 시점에서 다음 8 항목을 표현한다:</p>
     * <ul>
     *   <li>{@code dtm} — 1분 슬롯 시각 (actual·predc 공통 키).</li>
     *   <li>{@code actualElpwrAmt} — On 펌프 PWI GOOD 합산 (kW). 자정부터 현재시간까지 슬롯에만 존재, 미도래 시점은 NULL.</li>
     *   <li>{@code actualFlwrt} — 시설 직속 FLWMTR FRI GOOD 측정값 (m³/h). 미도래 시점 NULL.</li>
     *   <li>{@code actualUnitQty} — 계측 전력원단위 (kWh/m³). 분자/분모 0/NULL/BAD/부재 시 NULL.</li>
     *   <li>{@code predcElpwrAmt} — 예측 On 펌프 PWI 합산 (kW). 자정부터 익일 자정전까지 슬롯에 존재.</li>
     *   <li>{@code predcFlwrt} — 시설 직속 FLWMTR FRI 예측값 (m³/h).</li>
     *   <li>{@code predcUnitQty} — 예측 전력원단위 (kWh/m³). 분자/분모 0/NULL/부재 시 NULL.</li>
     *   <li>{@code predcPumpOnCnt} — 예측 가동 펌프 대수 (predc_val = 1.0 카운트 합산).</li>
     * </ul>
     *
     * <p>actual NULL 의미 — 해당 슬롯이 현재시간 이후로 도래하지 않았거나 측정 결측. predc NULL 의미 —
     * 예측 시계열이 해당 슬롯에 부재 (AI 추론 미수행 등). 두 의미가 분리되어 frontend 가 차트에서 actual·predc
     * 라인을 독립적으로 끊어 표시할 수 있도록 한다 (사용자 결정 — 10번 섹션 ANALYZE1).</p>
     */
    @Getter
    @Schema(description = "시계열 단일 포인트 (1분 슬롯) — actual 3 항목 + predc 4 항목")
    public static class DailyTimeSeriesPoint {

        @Schema(description = "1분 슬롯 시각 (actual·predc 공통 키)", example = "2026-05-27 10:30:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dtm;

        @Schema(description = "계측 — On 펌프 PWI GOOD 합산 (kW). 자정부터 현재시간까지 슬롯에만 존재 — "
                + "현재시간 이후 슬롯에서는 NULL (미도래)", example = "80.0000")
        private BigDecimal actualElpwrAmt;

        @Schema(description = "계측 — 시설 직속 FLWMTR FRI GOOD 측정값 (m³/h). 미도래 슬롯 또는 결측 시 NULL",
                example = "400.0000")
        private BigDecimal actualFlwrt;

        @Schema(description = "계측 전력원단위 (kWh/m³) — actualElpwrAmt / actualFlwrt. "
                + "분자/분모 0/NULL/BAD/부재 시 NULL", example = "0.2000")
        private BigDecimal actualUnitQty;

        @Schema(description = "예측 — 예측 On 펌프 PWI 합산 (kW). 자정부터 익일 자정전까지 슬롯에 존재 — "
                + "예측 미수행 슬롯은 NULL", example = "78.0000")
        private BigDecimal predcElpwrAmt;

        @Schema(description = "예측 — 시설 직속 FLWMTR FRI 예측값 (m³/h). 예측 미수행 슬롯 NULL",
                example = "390.0000")
        private BigDecimal predcFlwrt;

        @Schema(description = "예측 전력원단위 (kWh/m³) — predcElpwrAmt / predcFlwrt. "
                + "분자/분모 0/NULL/부재 시 NULL", example = "0.2000")
        private BigDecimal predcUnitQty;

        @Schema(description = "예측 가동 펌프 대수 — predc_val = 1.0 카운트 합산. 예측 미수행 슬롯 NULL",
                example = "2")
        private Integer predcPumpOnCnt;

        private DailyTimeSeriesPoint() {
        }

        /**
         * 1분 시점 데이터 정적 팩토리.
         *
         * @param dtm             1분 슬롯 시각
         * @param actualElpwrAmt  계측 On 펌프 PWI 합산
         * @param actualFlwrt     계측 FRI
         * @param actualUnitQty   계측 전력원단위
         * @param predcElpwrAmt   예측 On 펌프 PWI 합산
         * @param predcFlwrt      예측 FRI
         * @param predcUnitQty    예측 전력원단위
         * @param predcPumpOnCnt  예측 가동 펌프 대수
         * @return 구성된 시점 DTO
         */
        public static DailyTimeSeriesPoint of(
                LocalDateTime dtm,
                BigDecimal actualElpwrAmt,
                BigDecimal actualFlwrt,
                BigDecimal actualUnitQty,
                BigDecimal predcElpwrAmt,
                BigDecimal predcFlwrt,
                BigDecimal predcUnitQty,
                Integer predcPumpOnCnt) {
            DailyTimeSeriesPoint point = new DailyTimeSeriesPoint();
            point.dtm = dtm;
            point.actualElpwrAmt = actualElpwrAmt;
            point.actualFlwrt = actualFlwrt;
            point.actualUnitQty = actualUnitQty;
            point.predcElpwrAmt = predcElpwrAmt;
            point.predcFlwrt = predcFlwrt;
            point.predcUnitQty = predcUnitQty;
            point.predcPumpOnCnt = predcPumpOnCnt;
            return point;
        }
    }
}
