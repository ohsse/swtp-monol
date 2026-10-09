package com.mo.swtp.instrument.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 설비별 사용량 4번섹션 — 계측기 전력량 트렌드 응답 DTO (읽기 전용).
 *
 * <p>3번섹션에서 선택한 단일 계측기({@code instrumentId})의 조회기간 전력량(kWh)을 단일 시계열 계열로 표출한다.
 * 각 포인트는 그 계측기의 PWQ(적산전력량) 태그 버킷별 차분({@code MAX(raw_val)-MIN(raw_val)}) 으로 산정되며,
 * 계측기가 PWQ 태그를 다건 보유하면 동일 버킷끼리 합산된다 (설비별사용량-4번섹션 PLAN1 §구현 방향 3·4).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).
 * {@link PumpPowerTimeSeriesDto} outer + 중첩 Point + 정적 팩토리 패턴 인용.</p>
 */
@Getter
@Schema(description = "계측기 전력량 트렌드 응답 DTO — 설비별 사용량 4번섹션")
public class InstrumentEnergyTrendDto {

    @Schema(description = "계측기 ID (instrument_id)", example = "I-ELCMTR-001")
    private String instrumentId;

    @Schema(description = "계측기명", example = "송수1호기 전력량계")
    private String instrumentNm;

    @Schema(description = "측정 단위 (전력량)", example = "kWh")
    private String unit;

    @ArraySchema(schema = @Schema(description = "전력량 시계열 포인트 목록 (조회 단위 버킷별, 데이터 없는 버킷은 생략)",
            implementation = EnergyTrendPoint.class))
    private List<EnergyTrendPoint> points;

    private InstrumentEnergyTrendDto() {
    }

    /**
     * 계측기 전력량 트렌드 응답 DTO 정적 팩토리.
     *
     * @param instrumentId 계측기 ID (instrument_id)
     * @param instrumentNm 계측기명
     * @param unit         측정 단위 (kWh)
     * @param points       전력량 시계열 포인트 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static InstrumentEnergyTrendDto of(
            String instrumentId, String instrumentNm, String unit, List<EnergyTrendPoint> points) {
        InstrumentEnergyTrendDto dto = new InstrumentEnergyTrendDto();
        dto.instrumentId = instrumentId;
        dto.instrumentNm = instrumentNm;
        dto.unit = unit;
        dto.points = points;
        return dto;
    }

    /**
     * 전력량 시계열 단일 포인트 — 버킷 시작 일시 + 전력량 차분값.
     */
    @Getter
    @Schema(description = "전력량 시계열 단일 포인트 (조회 단위 버킷)")
    public static class EnergyTrendPoint {

        @Schema(description = "버킷 시작 일시 (시/일/월 단위 절삭값)", example = "2024-07-01 00:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime baseDtm;

        @Schema(description = "버킷 전력량 (kWh) — PWQ 적산값 MAX-MIN 차분 (다중 PWQ 태그 시 합산)",
                example = "120.5000")
        private BigDecimal elcegVal;

        private EnergyTrendPoint() {
        }

        /**
         * 전력량 시계열 포인트 정적 팩토리.
         *
         * @param baseDtm  버킷 시작 일시
         * @param elcegVal 버킷 전력량 (kWh)
         * @return 구성된 시점 DTO
         */
        public static EnergyTrendPoint of(LocalDateTime baseDtm, BigDecimal elcegVal) {
            EnergyTrendPoint point = new EnergyTrendPoint();
            point.baseDtm = baseDtm;
            point.elcegVal = elcegVal;
            return point;
        }
    }
}
