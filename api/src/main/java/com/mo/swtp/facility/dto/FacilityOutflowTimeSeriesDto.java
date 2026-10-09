package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 시설 단위 유출(송수) 유량·압력 + 펌프 가동상태 계측+예측 시계열 응답 DTO — 운전현황분석 7번 섹션.
 *
 * <p>활성 시설(useYn = Y, 지원 종류 PWTF/DWT/PRSF) 의 금일 하루치({@code 00:00 ~ 현재시간}) 시계열을
 * 1분 단위로 응답한다. 라인(유량·압력) 과 막대(펌프 가동상태) 를 두 시리즈로 분리한다:</p>
 * <ul>
 *   <li>{@code linePoints} — 시설 직속 FLWMTR 의 유출 유량(FRI)·압력(PRI) 계측+예측 라인 (1분 슬롯 목록).</li>
 *   <li>{@code pumpSeries} — 시설 소속 펌프(PUMP) 별 OPS 가동상태 계측+예측 막대 (펌프별 on/off 타임라인).</li>
 * </ul>
 *
 * <p>라인/막대를 분리해 펌프명을 슬롯마다 반복하지 않는다 (페이로드 경량화) — 차트의 라인/막대 구분과 정합
 * (사용자 결정 2026-06-01). 계측·예측 모두 {@code 금일 00:00 ~ 현재시간} 동일 구간이다 (10번 섹션처럼 익일
 * 자정/미래까지 가지 않음 — 사용자 결정 2026-06-01).</p>
 *
 * <p>10번 섹션 {@link FacilityDailyTimeSeriesDto} (전력원단위 단일 시계열) 와 관심사가 다르다 — 본 사이클은
 * 유량·압력·펌프 가동의 라인+막대 2-시리즈 응답이며 별도 DTO 로 분리한다 (사용자 메모리 "섹션별 사이클
 * 폐기·재설계 정책" 의 공존 케이스 — 10번 자산 무수정).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "시설 단위 유출 유량·압력 + 펌프 가동상태 계측+예측 시계열 — 운전현황분석 7번 섹션")
public class FacilityOutflowTimeSeriesDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "정수지A")
    private String facilityNm;

    @ArraySchema(schema = @Schema(
            description = "유출 유량·압력 라인 포인트 목록 (00:00 ~ 현재시간, 1분 간격, 시설 직속 FLWMTR 기준)",
            implementation = LinePoint.class))
    private List<LinePoint> linePoints;

    @ArraySchema(schema = @Schema(
            description = "펌프별 가동상태 막대 시리즈 목록 (펌프 1대 = 1 트랙). 시설 소속 모든 PUMP 포함",
            implementation = PumpSeries.class))
    private List<PumpSeries> pumpSeries;

    private FacilityOutflowTimeSeriesDto() {
    }

    /**
     * 시설 유출 시계열 응답 DTO 정적 팩토리.
     *
     * @param facilityId  시설 ID
     * @param facilityNm  시설명
     * @param linePoints  유량·압력 라인 포인트 목록 (빈 List 허용 — FLWMTR 부재 시)
     * @param pumpSeries  펌프별 가동상태 막대 시리즈 목록 (빈 List 허용 — PUMP 부재 시)
     * @return 구성된 응답 DTO
     */
    public static FacilityOutflowTimeSeriesDto of(
            String facilityId,
            String facilityNm,
            List<LinePoint> linePoints,
            List<PumpSeries> pumpSeries) {
        FacilityOutflowTimeSeriesDto dto = new FacilityOutflowTimeSeriesDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.linePoints = linePoints;
        dto.pumpSeries = pumpSeries;
        return dto;
    }

    /**
     * 유출 유량·압력 라인의 1분 단일 슬롯 — 계측(actual) 2 항목 + 예측(predc) 2 항목.
     *
     * <p>시설 직속 FLWMTR(첫 매치, 고정) 의 FRI(유량)·PRI(압력) 계측값·예측값을 표현한다. 계측은 GOOD 측정값만
     * (BAD/UNCERTAIN→NULL), 예측은 {@code predc_val} 직접 (QUALITY 개념 없음). 4 항목 모두 NULL 인 슬롯은
     * 응답에서 생략된다.</p>
     *
     * <p>actual NULL — 미도래 슬롯 또는 결측(BAD/UNCERTAIN) · predc NULL — 예측 미수행 슬롯. 두 의미가 분리되어
     * frontend 가 차트에서 계측·예측 라인을 독립적으로 끊어 표시할 수 있다.</p>
     */
    @Getter
    @Schema(description = "유출 유량·압력 라인 1분 슬롯 — 계측 2 항목 + 예측 2 항목")
    public static class LinePoint {

        @Schema(description = "1분 슬롯 시각 (계측·예측 공통 키)", example = "2026-06-01 10:30:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dtm;

        @Schema(description = "계측 유출 유량 (FRI, m³/h) — 시설 직속 FLWMTR GOOD 측정값. 미도래·결측·BAD/UNCERTAIN 시 NULL",
                example = "400.0000")
        private BigDecimal actualFlwrt;

        @Schema(description = "계측 유출 압력 (PRI, kgf/cm²) — 시설 직속 FLWMTR GOOD 측정값. 미도래·결측·BAD/UNCERTAIN 시 NULL",
                example = "2.5000")
        private BigDecimal actualPrsr;

        @Schema(description = "예측 유출 유량 (FRI, m³/h) — 시설 직속 FLWMTR 예측값. 예측 미수행 슬롯 NULL",
                example = "390.0000")
        private BigDecimal predcFlwrt;

        @Schema(description = "예측 유출 압력 (PRI, kgf/cm²) — 시설 직속 FLWMTR 예측값. 예측 미수행 슬롯 NULL",
                example = "2.4000")
        private BigDecimal predcPrsr;

        private LinePoint() {
        }

        /**
         * 라인 슬롯 정적 팩토리.
         *
         * @param dtm          1분 슬롯 시각
         * @param actualFlwrt  계측 유량
         * @param actualPrsr   계측 압력
         * @param predcFlwrt   예측 유량
         * @param predcPrsr    예측 압력
         * @return 구성된 라인 슬롯
         */
        public static LinePoint of(
                LocalDateTime dtm,
                BigDecimal actualFlwrt,
                BigDecimal actualPrsr,
                BigDecimal predcFlwrt,
                BigDecimal predcPrsr) {
            LinePoint point = new LinePoint();
            point.dtm = dtm;
            point.actualFlwrt = actualFlwrt;
            point.actualPrsr = actualPrsr;
            point.predcFlwrt = predcFlwrt;
            point.predcPrsr = predcPrsr;
            return point;
        }
    }

    /**
     * 펌프 1대의 가동상태 막대 시리즈 — 펌프 식별 + 1분 슬롯 목록.
     *
     * <p>시설 소속 모든 PUMP 가 각 1 트랙으로 포함된다 (OPS 태그·측정값이 없는 펌프도 {@code points} 빈 목록으로
     * 포함). 슬롯은 계측·예측 가동상태(on/off/불명) 를 담는다.</p>
     */
    @Getter
    @Schema(description = "펌프 1대 가동상태 막대 시리즈")
    public static class PumpSeries {

        @Schema(description = "펌프(계측기) ID", example = "in-xxx-xxx")
        private String instrumentId;

        @Schema(description = "펌프(계측기)명", example = "1호 송수펌프")
        private String instrumentNm;

        @ArraySchema(schema = @Schema(
                description = "펌프 가동상태 1분 슬롯 목록 (00:00 ~ 현재시간). 계측·예측 양쪽 불명(NULL) 슬롯은 생략",
                implementation = PumpPoint.class))
        private List<PumpPoint> points;

        private PumpSeries() {
        }

        /**
         * 펌프 막대 시리즈 정적 팩토리.
         *
         * @param instrumentId  펌프 ID
         * @param instrumentNm  펌프명
         * @param points        가동상태 슬롯 목록 (빈 List 허용 — OPS 부재 펌프)
         * @return 구성된 펌프 시리즈
         */
        public static PumpSeries of(
                String instrumentId,
                String instrumentNm,
                List<PumpPoint> points) {
            PumpSeries series = new PumpSeries();
            series.instrumentId = instrumentId;
            series.instrumentNm = instrumentNm;
            series.points = points;
            return series;
        }
    }

    /**
     * 펌프 가동상태 1분 단일 슬롯 — 계측(actual) tri-state + 예측(predc) tri-state.
     *
     * <p>{@code actualOps}·{@code predcOps} 은 계측·예측 테이블에서 조회한 OPS 원본값을 tri-state {@code Integer}
     * 로 노출한다 — {@code 1}=가동(on), {@code 0}=정지(off), {@code null}=불명. 막대 색칠은 {@code 1} 구간에
     * 적용한다 (Boolean true/false 변환 없이 원본 0/1 그대로).</p>
     *
     * <p>계측 불명(NULL) 의미 — OPS 측정값 결측 또는 QUALITY BAD/UNCERTAIN. on/off DI 신호이므로 BAD/UNCERTAIN
     * 을 정지(0) 로 표시하면 가동 중 펌프를 정지로 오인할 위험이 있어 불명(NULL) 으로 분리한다
     * ({@code ot-integration.md §3} OPS BAD 즉시 격상 정책 정합). 예측 불명(NULL) 의미 — 예측 미수행 또는
     * {@code predc_val} 결측. 10번 섹션의 2-state(가동/비가동) 판정과 의도가 분리된다 (운전현황분석-7번섹션 ANALYZE1
     * 안건 4).</p>
     */
    @Getter
    @Schema(description = "펌프 가동상태 1분 슬롯 — 계측 원본값(0/1) + 예측 원본값(0/1)")
    public static class PumpPoint {

        @Schema(description = "1분 슬롯 시각 (계측·예측 공통 키)", example = "2026-06-01 10:30:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime dtm;

        @Schema(description = "계측 가동상태 원본값 — 1=가동 / 0=정지 / null=불명(결측·BAD·UNCERTAIN). "
                + "BAD/UNCERTAIN 을 정지로 표시하지 않기 위해 불명으로 분리 (운전원 오인 방지)", example = "1")
        private Integer actualOps;

        @Schema(description = "예측 가동상태 원본값 — 1=가동 / 0=정지 / null=불명(예측 미수행·predc_val 결측)",
                example = "1")
        private Integer predcOps;

        private PumpPoint() {
        }

        /**
         * 펌프 가동상태 슬롯 정적 팩토리.
         *
         * @param dtm        1분 슬롯 시각
         * @param actualOps  계측 가동상태 원본값 (1/0/null)
         * @param predcOps   예측 가동상태 원본값 (1/0/null)
         * @return 구성된 가동상태 슬롯
         */
        public static PumpPoint of(
                LocalDateTime dtm,
                Integer actualOps,
                Integer predcOps) {
            PumpPoint point = new PumpPoint();
            point.dtm = dtm;
            point.actualOps = actualOps;
            point.predcOps = predcOps;
            return point;
        }
    }
}
