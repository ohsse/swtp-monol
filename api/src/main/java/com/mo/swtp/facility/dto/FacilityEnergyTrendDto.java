package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 시설별 사용량 5번섹션 — 운영시설 전력량 트렌드 조회 응답 DTO.
 *
 * <p>운영시설({@link com.mo.swtp.facility.domain.enumtype.FacilityGroup#OPERATION}) 1건의 전력량(kWh) 시계열을
 * 시설별 시리즈로 노출한다. 2번섹션 {@link FacilityEnergyUsageDto} 가 PWQ 적산 버킷 차분을 단일값으로 합쳐
 * 표출하는 것과 달리, 본 DTO 는 같은 버킷 차분을 집계단위 버킷별로 보존해 {@link #points} 시계열로 표출한다
 * (시설별사용량-5번섹션 PLAN1 §확정된 설계 결정).</p>
 *
 * <p>데이터 없는 버킷은 생략한다 — 연속 시간축 구성은 frontend 책임 (시설별사용량-5번섹션 ANALYZE1 결정,
 * {@link com.mo.swtp.opt.dto.PeakEnergyTrendDto} 빈 버킷 생략 선례 정합). 측정 데이터가 0건인 운영시설은
 * {@link #points} 빈 리스트로 응답에 포함된다 (0 측정 시설을 응답에서 누락하지 않음).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 합성/집계 뷰 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합, {@link FacilityEnergyUsageDto}·
 * {@code PeakEnergyTrendDto} 선례 동형). outer + 중첩 Point + 정적팩토리 패턴은 {@code PeakEnergyTrendDto}
 * 동형 미러링 (동형 패턴만 미러링, 섹션5 전용 신규 자산 — 사이클 간 자산 자동 원용 금지 정합). {@code elcegVal}
 * 필드명은 {@code PeakEnergyTrendPoint} 선례 그대로 ({@code elceg} + {@code val} 기존 등록 단어 조합).</p>
 */
@Getter
@Schema(description = "시설별 사용량 5번섹션 — 운영시설 전력량 트렌드 (시설별 시리즈)")
public class FacilityEnergyTrendDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "1단계 송수동")
    private String facilityNm;

    @ArraySchema(schema = @Schema(description = "전력량 시계열 포인트 목록 (집계단위 버킷별 PWQ 적산 차분 합, "
            + "baseDtm 오름차순, 데이터 없는 버킷은 생략, 측정 0건 시설은 빈 리스트)",
            implementation = EnergyTrendPoint.class))
    private List<EnergyTrendPoint> points;

    private FacilityEnergyTrendDto() {
    }

    /**
     * 운영시설 전력량 트렌드 응답 DTO 정적 팩토리.
     *
     * @param facilityId 시설 ID
     * @param facilityNm 시설명
     * @param points     전력량 시계열 (빈 List 허용 — 측정 0건 시설)
     * @return 구성된 응답 DTO
     */
    public static FacilityEnergyTrendDto of(
            String facilityId, String facilityNm, List<EnergyTrendPoint> points) {
        FacilityEnergyTrendDto dto = new FacilityEnergyTrendDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.points = points;
        return dto;
    }

    /**
     * 전력량 트렌드 시계열 단일 포인트 — 버킷 시작 일시 + 시설 합산 전력량.
     */
    @Getter
    @Schema(description = "전력량 트렌드 시계열 단일 포인트 (집계단위 버킷)")
    public static class EnergyTrendPoint {

        @Schema(description = "버킷 시작 일시 (집계단위 입도 — 시/일/월 절삭값)", example = "2026-01-15 14:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime baseDtm;

        @Schema(description = "버킷 시설 합산 전력량 (kWh) — 태그별 PWQ MAX-MIN 차분(음수 제외) 후 시설 합산",
                example = "240.5000")
        private BigDecimal elcegVal;

        private EnergyTrendPoint() {
        }

        /**
         * 전력량 트렌드 시계열 포인트 정적 팩토리.
         *
         * @param baseDtm  버킷 시작 일시
         * @param elcegVal 버킷 시설 합산 전력량 (kWh)
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
