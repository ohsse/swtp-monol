package com.mo.swtp.opt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 전력피크분석 5번섹션 — 전력량 추이(발생·예측) 조회 응답 DTO (읽기 전용).
 *
 * <p>현재 시각 기준 ±12시간(총 24시간) 윈도우의 시스템 전역 적산전력량(PWQ) 추세를 단일 응답으로 표출한다
 * (전력피크분석-5번섹션 PLAN1):</p>
 * <ul>
 *   <li><b>발생 전력량</b>({@code measuredPoints}) — 12시간 전 ~ 현재 시(時)의 실측 적산전력량 1시간 버킷 차분
 *       후 전역 합산 (kWh). 각 버킷은 {@code MAX(raw_val)-MIN(raw_val)} 차분 후 태그 합산</li>
 *   <li><b>예측 전력량</b>({@code predictedPoints}) — 현재 시(時) ~ 12시간 후의 예측 적산전력량 1시간 버킷 차분
 *       후 전역 합산 (kWh). 현재 시 버킷은 부분 집계</li>
 *   <li><b>요금적용전력피크</b>({@code billingPeakElpwr}) · <b>목표피크</b>({@code targetPeakElpwr}) —
 *       차트 가로 기준선 스칼라 (kW)</li>
 * </ul>
 *
 * <p>단위 혼재 — 시계열({@code measuredPoints}·{@code predictedPoints})은 전력량 kWh,
 * 스칼라({@code targetPeakElpwr}·{@code billingPeakElpwr})는 순시전력 kW. 백엔드는 단위 변환을 하지 않으며
 * 프론트가 이중축으로 표출한다 ({@code unit} 은 시계열 단위 "kWh" 만 표기).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 합성/집계 뷰 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).
 * 4번섹션 {@link PumpEnergyPredictionDto} outer+inner Point+정적팩토리 패턴 미러링 (동형 패턴만 미러링,
 * 섹션5 전용 신규 자산 — 사이클 간 자산 자동 원용 금지 정합).</p>
 */
@Getter
@Schema(description = "전력량 추이(발생·예측) 조회 응답 DTO — 전력피크분석 5번섹션")
public class PeakEnergyTrendDto {

    @Schema(description = "시계열 측정 단위 (전력량)", example = "kWh")
    private String unit;

    @Schema(description = "목표 피크 전력값 (kW, 차트 기준선)", example = "1500.0000")
    private BigDecimal targetPeakElpwr;

    @Schema(description = "요금적용전력피크 (kW, 차트 기준선) — 최근 12개월 분단위 PWI 합산의 MAX, 데이터 부재 시 0",
            example = "1320.5000")
    private BigDecimal billingPeakElpwr;

    @ArraySchema(schema = @Schema(description = "발생 전력량 시계열 포인트 목록 (12h전~현재, 1시간 버킷별 전역 합산, "
            + "데이터 없는 버킷은 생략)", implementation = PeakEnergyTrendPoint.class))
    private List<PeakEnergyTrendPoint> measuredPoints;

    @ArraySchema(schema = @Schema(description = "예측 전력량 시계열 포인트 목록 (현재~12h후, 1시간 버킷별 전역 합산, "
            + "현재 시 버킷은 부분 집계, 데이터 없는 버킷은 생략)", implementation = PeakEnergyTrendPoint.class))
    private List<PeakEnergyTrendPoint> predictedPoints;

    private PeakEnergyTrendDto() {
    }

    /**
     * 전력량 추이 응답 DTO 정적 팩토리.
     *
     * @param unit             시계열 측정 단위 (kWh)
     * @param targetPeakElpwr  목표 피크 전력값 (kW)
     * @param billingPeakElpwr 요금적용전력피크 (kW)
     * @param measuredPoints   발생 전력량 시계열 (빈 List 허용)
     * @param predictedPoints  예측 전력량 시계열 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static PeakEnergyTrendDto of(
            String unit,
            BigDecimal targetPeakElpwr,
            BigDecimal billingPeakElpwr,
            List<PeakEnergyTrendPoint> measuredPoints,
            List<PeakEnergyTrendPoint> predictedPoints) {
        PeakEnergyTrendDto dto = new PeakEnergyTrendDto();
        dto.unit = unit;
        dto.targetPeakElpwr = targetPeakElpwr;
        dto.billingPeakElpwr = billingPeakElpwr;
        dto.measuredPoints = measuredPoints;
        dto.predictedPoints = predictedPoints;
        return dto;
    }

    /**
     * 전력량 추이 시계열 단일 포인트 — 버킷 시작 일시 + 전역 합산 전력량.
     */
    @Getter
    @Schema(description = "전력량 추이 시계열 단일 포인트 (1시간 버킷)")
    public static class PeakEnergyTrendPoint {

        @Schema(description = "버킷 시작 일시 (시 단위 절삭값)", example = "2026-06-05 10:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime baseDtm;

        @Schema(description = "버킷 전역 합산 전력량 (kWh) — 태그별 PWQ MAX-MIN 차분 후 합산", example = "240.5000")
        private BigDecimal elcegVal;

        private PeakEnergyTrendPoint() {
        }

        /**
         * 전력량 추이 시계열 포인트 정적 팩토리.
         *
         * @param baseDtm  버킷 시작 일시
         * @param elcegVal 버킷 전역 합산 전력량 (kWh)
         * @return 구성된 시점 DTO
         */
        public static PeakEnergyTrendPoint of(LocalDateTime baseDtm, BigDecimal elcegVal) {
            PeakEnergyTrendPoint point = new PeakEnergyTrendPoint();
            point.baseDtm = baseDtm;
            point.elcegVal = elcegVal;
            return point;
        }
    }
}
