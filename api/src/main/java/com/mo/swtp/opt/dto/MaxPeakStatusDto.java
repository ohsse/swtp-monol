package com.mo.swtp.opt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Getter;

/**
 * 사용량트렌드 3번섹션 — 최대 피크 현황 조회 응답 DTO (읽기 전용).
 *
 * <p>현재 월 포함 최근 6개월의 월별 최대 피크(kW)를 6개 월 슬롯으로 표출한다 (사용량트렌드-3번섹션 PLAN1).
 * 각 월 피크는 그 달의 분(分)별 전체 활성 PWI(순시전력) 태그 합산값 중 최댓값
 * ({@code MAX_over_month(SUM_over_facilities(PWI per minute))}) 이다. 데이터가 없는 달은 {@code peakVal=null}
 * 로 채워 슬롯 수가 항상 6 이도록 보장한다 (sparse 쿼리 결과를 Service 가 6슬롯으로 병합).</p>
 *
 * <p>2번섹션 {@link EnergyUsageTrendDto}(전력량 kWh 시계열) 와 달리 순시전력 피크(kW)의 월별 단일값 시계열이다
 * ({@code unit = "kW"}). {@code BaseAuditResponseDto} 미상속 — 합성/집계 뷰 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합). outer+inner Point+정적팩토리
 * 패턴만 미러링하며 3번섹션 전용 신규 자산이다 (사이클 간 자산 자동 원용 금지 정합).</p>
 */
@Getter
@Schema(description = "최대 피크 현황 조회 응답 DTO — 사용량트렌드 3번섹션")
public class MaxPeakStatusDto {

    @Schema(description = "측정 단위 (순시전력)", example = "kW")
    private String unit;

    @ArraySchema(schema = @Schema(description = "월별 최대 피크 포인트 목록 (현재 월 포함 최근 6개월, 항상 6건, "
            + "월 시작 일시 오름차순, 데이터 없는 달 peakVal=null)", implementation = MaxPeakStatusPoint.class))
    private List<MaxPeakStatusPoint> points;

    private MaxPeakStatusDto() {
    }

    /**
     * 최대 피크 현황 응답 DTO 정적 팩토리.
     *
     * @param unit   측정 단위 (kW)
     * @param points 월별 최대 피크 6슬롯 (오름차순, 결측 월 {@code peakVal=null})
     * @return 구성된 응답 DTO
     */
    public static MaxPeakStatusDto of(String unit, List<MaxPeakStatusPoint> points) {
        MaxPeakStatusDto dto = new MaxPeakStatusDto();
        dto.unit = unit;
        dto.points = points;
        return dto;
    }

    /**
     * 최대 피크 현황 단일 포인트 — 월 시작 일시 + 그 달의 최대 피크.
     */
    @Getter
    @Schema(description = "월별 최대 피크 단일 포인트")
    public static class MaxPeakStatusPoint {

        @Schema(description = "월 시작 일시 (해당 월 1일 00:00)", example = "2026-06-01 00:00:00")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
        private LocalDateTime baseDtm;

        @Schema(description = "월별 최대 순시전력 피크 (kW) — 분별 전체 PWI 합산값 중 최댓값. 데이터 없는 달 null",
                example = "1047.4000", nullable = true)
        private BigDecimal peakVal;

        private MaxPeakStatusPoint() {
        }

        /**
         * 최대 피크 현황 시점 포인트 정적 팩토리.
         *
         * @param baseDtm 월 시작 일시
         * @param peakVal 월별 최대 피크 (kW), 데이터 없는 달 {@code null}
         * @return 구성된 시점 DTO
         */
        public static MaxPeakStatusPoint of(LocalDateTime baseDtm, BigDecimal peakVal) {
            MaxPeakStatusPoint point = new MaxPeakStatusPoint();
            point.baseDtm = baseDtm;
            point.peakVal = peakVal;
            return point;
        }
    }
}
