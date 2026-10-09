package com.mo.swtp.instrument.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 송수펌프 가동이력 3번섹션 — 펌프별 전력량 시계열 응답 DTO (읽기 전용).
 *
 * <p>전체 활성 송수펌프의 조회기간 전력량(kWh)을 펌프별 계열로 표출한다. 각 계열은 PWQ(적산전력량) 태그의
 * 버킷별 차분({@code MAX(raw_val)-MIN(raw_val)}) 으로 산정된 시계열 포인트 목록을 갖는다
 * (송수펌프가동이력_3번섹션 PLAN1 §구현 방향 5).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).
 * {@link com.mo.swtp.facility.dto.FacilityDailyTimeSeriesDto} outer+inner Point+정적팩토리 패턴 인용.</p>
 */
@Getter
@Schema(description = "펌프별 전력량 시계열 응답 DTO — 송수펌프 가동이력 3번섹션")
public class PumpPowerTimeSeriesDto {

    @Schema(description = "펌프 ID (instrument_id)", example = "I-PUMP-001")
    private String pumpId;

    @Schema(description = "펌프명", example = "송수1호기")
    private String pumpNm;

    @Schema(description = "측정 단위 (전력량)", example = "kWh")
    private String unit;

    @ArraySchema(schema = @Schema(description = "전력량 시계열 포인트 목록 (조회 단위 버킷별, 데이터 없는 버킷은 생략)",
            implementation = PumpPowerTimeSeriesPoint.class))
    private List<PumpPowerTimeSeriesPoint> points;

    private PumpPowerTimeSeriesDto() {
    }

    /**
     * 펌프별 전력량 시계열 응답 DTO 정적 팩토리.
     *
     * @param pumpId 펌프 ID (instrument_id)
     * @param pumpNm 펌프명
     * @param unit   측정 단위 (kWh)
     * @param points 전력량 시계열 포인트 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static PumpPowerTimeSeriesDto of(
            String pumpId, String pumpNm, String unit, List<PumpPowerTimeSeriesPoint> points) {
        PumpPowerTimeSeriesDto dto = new PumpPowerTimeSeriesDto();
        dto.pumpId = pumpId;
        dto.pumpNm = pumpNm;
        dto.unit = unit;
        dto.points = points;
        return dto;
    }

    /**
     * 전력량 시계열 단일 포인트 — 버킷 시작 일시 + 전력량 차분값.
     */
    @Getter
    @Schema(description = "전력량 시계열 단일 포인트 (조회 단위 버킷)")
    public static class PumpPowerTimeSeriesPoint {

        @Schema(description = "버킷 시작 일시 (시/일/월/년 단위 절삭값)", example = "2024-07-01 00:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime baseDtm;

        @Schema(description = "버킷 전력량 (kWh) — PWQ 적산값 MAX-MIN 차분", example = "120.5000")
        private BigDecimal elcegVal;

        private PumpPowerTimeSeriesPoint() {
        }

        /**
         * 전력량 시계열 포인트 정적 팩토리.
         *
         * @param baseDtm  버킷 시작 일시
         * @param elcegVal 버킷 전력량 (kWh)
         * @return 구성된 시점 DTO
         */
        public static PumpPowerTimeSeriesPoint of(LocalDateTime baseDtm, BigDecimal elcegVal) {
            PumpPowerTimeSeriesPoint point = new PumpPowerTimeSeriesPoint();
            point.baseDtm = baseDtm;
            point.elcegVal = elcegVal;
            return point;
        }
    }
}
