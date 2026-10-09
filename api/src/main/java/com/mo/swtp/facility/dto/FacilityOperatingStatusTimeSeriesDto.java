package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.facility.domain.enumtype.FacilityOperatingStatusCompareType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import lombok.Getter;

/**
 * 시설 단위 운영 현황 시계열 응답 DTO — 운전현황분석 5번 섹션 (옵션 T).
 *
 * <p>활성 시설(useYn = Y, 지원 종류 PWTF/DWT/PRSF) 의 금일 실측과 비교 기간(YESTERDAY 또는 LAST_WEEK)
 * 실측을 <b>시간:분(HH:mm) 키로 머지한 1440 고정 단일 시계열</b>({@code series}) 로 응답한다
 * (운전현황분석-5번섹션-DTO재설계 PLAN1). 금일·비교일이 서로 다른 날짜이므로 머지 키는 날짜를 제거한
 * "HH:mm" 이며, 프론트엔드는 별도 머지 없이 {@code series} 를 그대로 차트 X축에 매핑한다.</p>
 *
 * <p>{@code series} 는 00:00 ~ 23:59 의 1440 슬롯을 모두 포함한다. 금일·비교 둘 다 결측인 시점도
 * 행은 존재하되 3 시리즈 컬럼이 모두 null 이다 (1440 고정 — PLAN1 가정 결정).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 *
 * <p>전력원단위 산정식은 4번 섹션과 동일 — {@code sum(On 펌프 PWI GOOD) / 시설 직속 FLWMTR FRI GOOD}.
 * 1분 시점 단위로 적용 (운전현황분석-5번섹션 ANALYZE1).</p>
 */
@Getter
@Schema(description = "시설 단위 운영 현황 시계열 — 운전현황분석 5번 섹션")
public class FacilityOperatingStatusTimeSeriesDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "정수지A")
    private String facilityNm;

    @Schema(description = "기준 일자 (금일)", example = "2026-05-21")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate baseDate;

    @Schema(description = "비교 기간 선택값", implementation = FacilityOperatingStatusCompareType.class)
    private FacilityOperatingStatusCompareType compareType;

    @Schema(description = "비교 일자 — compareType 에 따른 실제 날짜 (YESTERDAY: -1d, LAST_WEEK: -7d)",
            example = "2026-05-20")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    private LocalDate comparisonDate;

    @ArraySchema(schema = @Schema(
            description = "시간:분 키 1440 슬롯 시계열 (00:00 ~ 23:59, 1분 간격, 결측 시점은 컬럼 null 채움)",
            implementation = TimeSeriesPoint.class))
    private List<TimeSeriesPoint> series;

    private FacilityOperatingStatusTimeSeriesDto() {
    }

    /**
     * 시설 운영 현황 시계열 응답 DTO 정적 팩토리.
     *
     * @param facilityId     시설 ID
     * @param facilityNm     시설명
     * @param baseDate       기준 일자 (금일)
     * @param compareType    요청한 비교 옵션
     * @param comparisonDate 비교 일자 (compareType 에 따른 실제 날짜)
     * @param series         1440 고정 머지 시계열
     * @return 구성된 응답 DTO
     */
    public static FacilityOperatingStatusTimeSeriesDto of(
            String facilityId,
            String facilityNm,
            LocalDate baseDate,
            FacilityOperatingStatusCompareType compareType,
            LocalDate comparisonDate,
            List<TimeSeriesPoint> series) {
        FacilityOperatingStatusTimeSeriesDto dto = new FacilityOperatingStatusTimeSeriesDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.baseDate = baseDate;
        dto.compareType = compareType;
        dto.comparisonDate = comparisonDate;
        dto.series = series;
        return dto;
    }

    /**
     * 시간:분 시점 — 1분 슬롯의 3 시리즈 머지 행.
     *
     * <p>각 시점에서 다음 3 시리즈를 표현한다:</p>
     * <ul>
     *   <li>{@code todayElpwrUnitQty} — 시리즈 1, 금일 실측 전력원단위 (kWh/m³). 해당 시점 실측 부재 또는
     *       분자/분모 0/NULL/BAD 시 null.</li>
     *   <li>{@code comparisonElpwrUnitQty} — 시리즈 2, 비교일 실측 전력원단위 (kWh/m³). 동일 규칙.</li>
     *   <li>{@code todayOnPumpCnt} — 시리즈 3, 금일 실측 운영 펌프 대수 (OPS = GOOD + 1.0). 해당 시점
     *       실측 부재 시 null.</li>
     * </ul>
     */
    @Getter
    @Schema(description = "시간:분 시점 (1분 슬롯) — 3 시리즈 머지 행")
    public static class TimeSeriesPoint {

        @Schema(description = "시간:분 (HH:mm)", example = "14:30")
        private String time;

        @Schema(description = "시리즈 1 — 금일 실측 전력원단위 (kWh/m³). "
                + "분자/분모 0·NULL·BAD·부재 시 또는 해당 시점 실측 부재 시 null", example = "0.4200")
        private BigDecimal todayElpwrUnitQty;

        @Schema(description = "시리즈 2 — 비교일 실측 전력원단위 (kWh/m³). "
                + "분자/분모 0·NULL·BAD·부재 시 또는 해당 시점 비교 데이터 부재 시 null", example = "0.4100")
        private BigDecimal comparisonElpwrUnitQty;

        @Schema(description = "시리즈 3 — 금일 실측 운영 펌프 대수. quality_cd=GOOD 이고 가동상태값 1.0인 펌프만 카운트. "
                + "UNCERTAIN/BAD OPS 태그 제외 (ot-integration.md §3 OPS 즉시 BAD 격상 정책). "
                + "해당 시점 데이터 부재 시 null", example = "2")
        private Integer todayOnPumpCnt;

        private TimeSeriesPoint() {
        }

        /**
         * 시간:분 시점 데이터 정적 팩토리.
         *
         * @param time                   시간:분 (HH:mm)
         * @param todayElpwrUnitQty      금일 실측 전력원단위 (무효 시 null)
         * @param comparisonElpwrUnitQty 비교일 실측 전력원단위 (무효 시 null)
         * @param todayOnPumpCnt         금일 실측 운영 펌프 대수 (실측 부재 시 null)
         * @return 구성된 시점 DTO
         */
        public static TimeSeriesPoint of(
                String time,
                BigDecimal todayElpwrUnitQty,
                BigDecimal comparisonElpwrUnitQty,
                Integer todayOnPumpCnt) {
            TimeSeriesPoint point = new TimeSeriesPoint();
            point.time = time;
            point.todayElpwrUnitQty = todayElpwrUnitQty;
            point.comparisonElpwrUnitQty = comparisonElpwrUnitQty;
            point.todayOnPumpCnt = todayOnPumpCnt;
            return point;
        }
    }
}
