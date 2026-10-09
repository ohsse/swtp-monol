package com.mo.swtp.opt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 전력피크분석 4번섹션 — 시설 펌프 전력량 예측 시계열 응답 DTO (읽기 전용).
 *
 * <p>선택 시설이 보유한 활성 송수펌프({@code equip_type_cd = 'PUMP'} + {@code use_yn = Y}) 들의 적산전력량(PWQ)
 * 예측값을 현재 시(時)부터 24시간까지 1시간 버킷 단위로 합산한 단일 시계열을 표출한다. 각 버킷 전력량은
 * 펌프별 예측 적산값의 버킷별 차분({@code MAX(predc_val)-MIN(predc_val)}) 후 시설 합산이다
 * (전력피크분석-4번섹션 PLAN1 §기능2).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 합성/집계 뷰 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).
 * {@link com.mo.swtp.instrument.dto.PumpPowerTimeSeriesDto} outer+inner Point+정적팩토리 패턴 인용
 * (사이클 간 자산 자동 원용 금지 — 동형 패턴만 미러링, 섹션4 전용 신규 자산).</p>
 */
@Getter
@Schema(description = "시설 펌프 전력량 예측 시계열 응답 DTO — 전력피크분석 4번섹션")
public class PumpEnergyPredictionDto {

    @Schema(description = "시설 ID (facility_id)", example = "F-PWTF-001")
    private String facilityId;

    @Schema(description = "시설명", example = "1정수지")
    private String facilityNm;

    @Schema(description = "측정 단위 (전력량)", example = "kWh")
    private String unit;

    @ArraySchema(schema = @Schema(description = "전력량 예측 시계열 포인트 목록 (1시간 버킷별 시설 합산, 데이터 없는 버킷은 생략)",
            implementation = PumpEnergyPredictionPoint.class))
    private List<PumpEnergyPredictionPoint> points;

    private PumpEnergyPredictionDto() {
    }

    /**
     * 시설 펌프 전력량 예측 시계열 응답 DTO 정적 팩토리.
     *
     * @param facilityId 시설 ID
     * @param facilityNm 시설명
     * @param unit       측정 단위 (kWh)
     * @param points     전력량 예측 시계열 포인트 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static PumpEnergyPredictionDto of(
            String facilityId, String facilityNm, String unit, List<PumpEnergyPredictionPoint> points) {
        PumpEnergyPredictionDto dto = new PumpEnergyPredictionDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.unit = unit;
        dto.points = points;
        return dto;
    }

    /**
     * 전력량 예측 시계열 단일 포인트 — 버킷 시작 일시 + 시설 합산 예측 전력량.
     */
    @Getter
    @Schema(description = "전력량 예측 시계열 단일 포인트 (1시간 버킷)")
    public static class PumpEnergyPredictionPoint {

        @Schema(description = "버킷 시작 일시 (시 단위 절삭값)", example = "2026-06-05 10:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime baseDtm;

        @Schema(description = "버킷 시설 합산 예측 전력량 (kWh) — 펌프별 PWQ 예측 MAX-MIN 차분 후 합산", example = "240.5000")
        private BigDecimal elcegVal;

        private PumpEnergyPredictionPoint() {
        }

        /**
         * 전력량 예측 시계열 포인트 정적 팩토리.
         *
         * @param baseDtm  버킷 시작 일시
         * @param elcegVal 버킷 시설 합산 예측 전력량 (kWh)
         * @return 구성된 시점 DTO
         */
        public static PumpEnergyPredictionPoint of(LocalDateTime baseDtm, BigDecimal elcegVal) {
            PumpEnergyPredictionPoint point = new PumpEnergyPredictionPoint();
            point.baseDtm = baseDtm;
            point.elcegVal = elcegVal;
            return point;
        }
    }
}
