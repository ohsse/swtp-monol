package com.mo.swtp.opt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 사용량트렌드 2번섹션 — 정수장 전체 전력량 추이 조회 응답 DTO (읽기 전용).
 *
 * <p>사용자 지정 기간({@code fromDt~toDt} 익일 00시 이전)의 정수장 전체 적산전력량(PWQ) 추세를 집계단위
 * (시/일/월) 버킷별 단일 시계열로 표출한다 (사용량트렌드-2번섹션 PLAN1). 각 버킷 전력량은 전체 활성 PWQ
 * 태그의 적산값 차분({@code MAX(raw_val)-MIN(raw_val)}) 을 버킷 단위로 전역 합산한 값(kWh)이며, 데이터가
 * 없는 버킷은 생략된다 (sparse — 프론트가 빈 구간 처리).</p>
 *
 * <p>5번섹션 {@link PeakEnergyTrendDto} 와 달리 발생/예측 이중 시계열·요금/목표 스칼라가 없는 단일 실측
 * 시계열이다 ({@code unit = "kWh"}). {@code BaseAuditResponseDto} 미상속 — 합성/집계 뷰 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합). {@link PeakEnergyTrendDto}
 * outer+inner Point+정적팩토리 패턴 미러링 (동형 패턴만 미러링, 섹션2 전용 신규 자산 — 사이클 간 자산 자동
 * 원용 금지 정합).</p>
 */
@Getter
@Schema(description = "정수장 전체 전력량 추이 조회 응답 DTO — 사용량트렌드 2번섹션")
public class EnergyUsageTrendDto {

    @Schema(description = "시계열 측정 단위 (전력량)", example = "kWh")
    private String unit;

    @ArraySchema(schema = @Schema(description = "전력량 시계열 포인트 목록 (집계단위 버킷별 전역 합산, "
            + "데이터 없는 버킷은 생략, 버킷 시작 일시 오름차순)", implementation = EnergyUsageTrendPoint.class))
    private List<EnergyUsageTrendPoint> points;

    private EnergyUsageTrendDto() {
    }

    /**
     * 전력량 추이 응답 DTO 정적 팩토리.
     *
     * @param unit   시계열 측정 단위 (kWh)
     * @param points 전력량 시계열 (빈 List 허용)
     * @return 구성된 응답 DTO
     */
    public static EnergyUsageTrendDto of(String unit, List<EnergyUsageTrendPoint> points) {
        EnergyUsageTrendDto dto = new EnergyUsageTrendDto();
        dto.unit = unit;
        dto.points = points;
        return dto;
    }

    /**
     * 전력량 추이 시계열 단일 포인트 — 버킷 시작 일시 + 전역 합산 전력량.
     */
    @Getter
    @Schema(description = "전력량 추이 시계열 단일 포인트 (집계단위 버킷)")
    public static class EnergyUsageTrendPoint {

        @Schema(description = "버킷 시작 일시 (집계단위 절삭값)", example = "2026-01-15 00:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime baseDtm;

        @Schema(description = "버킷 전역 합산 전력량 (kWh) — 전체 PWQ 태그 MAX-MIN 차분 후 합산", example = "1240.5000")
        private BigDecimal elcegVal;

        private EnergyUsageTrendPoint() {
        }

        /**
         * 전력량 추이 시계열 포인트 정적 팩토리.
         *
         * @param baseDtm  버킷 시작 일시
         * @param elcegVal 버킷 전역 합산 전력량 (kWh)
         * @return 구성된 시점 DTO
         */
        public static EnergyUsageTrendPoint of(LocalDateTime baseDtm, BigDecimal elcegVal) {
            EnergyUsageTrendPoint point = new EnergyUsageTrendPoint();
            point.baseDtm = baseDtm;
            point.elcegVal = elcegVal;
            return point;
        }
    }
}
