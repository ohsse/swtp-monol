package com.mo.swtp.instrument.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 송수펌프 가동이력 3번섹션 — 펌프별 주파수 시계열 응답 DTO (읽기 전용).
 *
 * <p><strong>인버터 펌프({@code INVERTER_DRIVE})만</strong> 대상으로 가변 주파수(Hz)를 펌프별 계열로 표출한다.
 * 정격 펌프({@code RATED_DRIVE})는 주파수가 고정이라 제외된다. 각 계열은 FQI 태그의 버킷별 평균
 * ({@code AVG(COALESCE(corr_val,raw_val))}) 으로 산정된 시계열 포인트 목록을 갖는다
 * (송수펌프가동이력_3번섹션 PLAN1 §구현 방향 5).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).
 * {@link PumpPowerTimeSeriesDto} 및 {@link com.mo.swtp.facility.dto.FacilityDailyTimeSeriesDto}
 * outer+inner Point+정적팩토리 패턴 인용.</p>
 */
@Getter
@Schema(description = "펌프별 주파수 시계열 응답 DTO (인버터 펌프 전용) — 송수펌프 가동이력 3번섹션")
public class PumpFrequencyTimeSeriesDto {

    @Schema(description = "펌프 ID (instrument_id)", example = "I-PUMP-002")
    private String pumpId;

    @Schema(description = "펌프명", example = "송수2호기")
    private String pumpNm;

    @Schema(description = "측정 단위 (주파수)", example = "Hz")
    private String unit;

    @ArraySchema(schema = @Schema(description = "주파수 시계열 포인트 목록 (조회 단위 버킷별, 데이터 없는 버킷은 생략)",
            implementation = PumpFrequencyTimeSeriesPoint.class))
    private List<PumpFrequencyTimeSeriesPoint> points;

    private PumpFrequencyTimeSeriesDto() {
    }

    /**
     * 펌프별 주파수 시계열 응답 DTO 정적 팩토리.
     *
     * @param pumpId 펌프 ID (instrument_id)
     * @param pumpNm 펌프명
     * @param unit   측정 단위 (Hz)
     * @param points 주파수 시계열 포인트 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static PumpFrequencyTimeSeriesDto of(
            String pumpId, String pumpNm, String unit, List<PumpFrequencyTimeSeriesPoint> points) {
        PumpFrequencyTimeSeriesDto dto = new PumpFrequencyTimeSeriesDto();
        dto.pumpId = pumpId;
        dto.pumpNm = pumpNm;
        dto.unit = unit;
        dto.points = points;
        return dto;
    }

    /**
     * 주파수 시계열 단일 포인트 — 버킷 시작 일시 + 주파수 평균값.
     */
    @Getter
    @Schema(description = "주파수 시계열 단일 포인트 (조회 단위 버킷)")
    public static class PumpFrequencyTimeSeriesPoint {

        @Schema(description = "버킷 시작 일시 (시/일/월/년 단위 절삭값)", example = "2024-07-01 00:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime baseDtm;

        @Schema(description = "버킷 주파수 평균 (Hz) — FQI AVG(COALESCE(corr_val,raw_val))", example = "45.2000")
        private BigDecimal freqVal;

        private PumpFrequencyTimeSeriesPoint() {
        }

        /**
         * 주파수 시계열 포인트 정적 팩토리.
         *
         * @param baseDtm 버킷 시작 일시
         * @param freqVal 버킷 주파수 평균 (Hz)
         * @return 구성된 시점 DTO
         */
        public static PumpFrequencyTimeSeriesPoint of(LocalDateTime baseDtm, BigDecimal freqVal) {
            PumpFrequencyTimeSeriesPoint point = new PumpFrequencyTimeSeriesPoint();
            point.baseDtm = baseDtm;
            point.freqVal = freqVal;
            return point;
        }
    }
}
