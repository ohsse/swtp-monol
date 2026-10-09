package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.instrument.domain.enumtype.EquipType;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 설비별 사용량 7번섹션 — 설비별 순시전력 트렌드 응답 DTO (읽기 전용).
 *
 * <p>2번섹션에서 선택한 시설을 루트로 하는 재귀 하위 트리 전체(루트 inclusive)의 활성 계측기 중 PWI(순시전력)
 * 태그 보유 계측기 각각의 조회기간 순시전력(kW) 1분 시계열을 설비별 멀티시리즈로 노출한다. 한 설비가 PWI 태그를
 * 다건 보유하면 동일 {@code acqDtm} 끼리 합산된 단일 시리즈로 표출한다 (설비별사용량-7번섹션 PLAN1 §구현 방향).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합, {@link FacilityInstrumentEnergyUsageDto}·
 * {@link FacilityPowerInstrumentDto} 선례 동형). outer + 중첩 {@link InstrumentPowerSeries} + 중첩
 * {@link PowerTrendPoint} + 정적 팩토리 패턴은 {@link com.mo.swtp.instrument.dto.InstrumentEnergyTrendDto}(시계열) +
 * {@link FacilityInstrumentEnergyUsageDto}(설비별 멀티) 구조 미러링 (7번섹션 전용 신규 자산 — 사이클 간 자산 자동
 * 원용 금지 정합).</p>
 */
@Getter
@Schema(description = "설비별 사용량 7번섹션 — 설비별 순시전력 트렌드 응답 DTO")
public class FacilityInstrumentPowerTrendDto {

    @Schema(description = "측정 단위 (순시전력)", example = "kW")
    private String unit;

    @ArraySchema(schema = @Schema(
            description = "설비별 순시전력 시계열 시리즈 목록 (PWI 보유 계측기, 데이터 0 설비도 빈 points 시리즈로 포함)",
            implementation = InstrumentPowerSeries.class))
    private List<InstrumentPowerSeries> series;

    private FacilityInstrumentPowerTrendDto() {
    }

    /**
     * 설비별 순시전력 트렌드 응답 DTO 정적 팩토리.
     *
     * @param unit   측정 단위 (kW)
     * @param series 설비별 시계열 시리즈 목록 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static FacilityInstrumentPowerTrendDto of(String unit, List<InstrumentPowerSeries> series) {
        FacilityInstrumentPowerTrendDto dto = new FacilityInstrumentPowerTrendDto();
        dto.unit = unit;
        dto.series = series;
        return dto;
    }

    /**
     * 계측기(설비) 1개의 순시전력 시계열 시리즈 — 차트 라인 1개에 대응한다.
     */
    @Getter
    @Schema(description = "설비(계측기)별 순시전력 시계열 시리즈")
    public static class InstrumentPowerSeries {

        @Schema(description = "계측기 ID", example = "in-xxx-xxx")
        private String instrumentId;

        @Schema(description = "계측기명", example = "송수1호기 전력계")
        private String instrumentNm;

        @Schema(description = "장비 유형 코드 (PUMP/VALVE/FLWMTR/PRSMTR/LVMTR/ELCMTR)",
                implementation = EquipType.class)
        private EquipType equipTypeCd;

        @Schema(description = "소속 시설 ID", example = "fa-xxx-xxx")
        private String facilityId;

        @Schema(description = "소속 시설명", example = "생활송수동")
        private String facilityNm;

        @ArraySchema(schema = @Schema(
                description = "순시전력 1분 시계열 포인트 목록 (acqDtm 오름차순, GOOD 품질 합산값만)",
                implementation = PowerTrendPoint.class))
        private List<PowerTrendPoint> points;

        private InstrumentPowerSeries() {
        }

        /**
         * 설비별 순시전력 시계열 시리즈 정적 팩토리.
         *
         * @param instrumentId 계측기 ID
         * @param instrumentNm 계측기명
         * @param equipTypeCd  장비 유형 코드
         * @param facilityId   소속 시설 ID
         * @param facilityNm   소속 시설명
         * @param points       순시전력 시계열 포인트 목록 (빈 List 허용 — 데이터 0 설비)
         * @return 구성된 시리즈 DTO
         */
        public static InstrumentPowerSeries of(
                String instrumentId,
                String instrumentNm,
                EquipType equipTypeCd,
                String facilityId,
                String facilityNm,
                List<PowerTrendPoint> points) {
            InstrumentPowerSeries s = new InstrumentPowerSeries();
            s.instrumentId = instrumentId;
            s.instrumentNm = instrumentNm;
            s.equipTypeCd = equipTypeCd;
            s.facilityId = facilityId;
            s.facilityNm = facilityNm;
            s.points = points;
            return s;
        }
    }

    /**
     * 순시전력 시계열 단일 포인트 — 측정 분(分) 시각 + 설비합 순시전력값.
     */
    @Getter
    @Schema(description = "순시전력 시계열 단일 포인트 (1분 단위)")
    public static class PowerTrendPoint {

        @Schema(description = "측정 분(分) 시각", example = "2026-01-01 00:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime acqDtm;

        @Schema(description = "설비 순시전력 (kW) — 그 분의 GOOD 품질 PWI 태그 COALESCE(corr_val, raw_val) 합산",
                example = "120.5000")
        private BigDecimal elpwrVal;

        private PowerTrendPoint() {
        }

        /**
         * 순시전력 시계열 포인트 정적 팩토리.
         *
         * @param acqDtm   측정 분(分) 시각
         * @param elpwrVal 설비 순시전력 (kW)
         * @return 구성된 시점 DTO
         */
        public static PowerTrendPoint of(LocalDateTime acqDtm, BigDecimal elpwrVal) {
            PowerTrendPoint point = new PowerTrendPoint();
            point.acqDtm = acqDtm;
            point.elpwrVal = elpwrVal;
            return point;
        }
    }
}
