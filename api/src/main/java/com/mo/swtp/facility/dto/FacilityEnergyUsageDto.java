package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 시설별 사용량 2번섹션 — 운영시설 전력 사용량 응답 DTO.
 *
 * <p>운영시설({@link com.mo.swtp.facility.domain.enumtype.FacilityGroup#OPERATION}) 1건당 4지표를 노출한다:</p>
 * <ul>
 *   <li>{@link #elpwr} 순시전력 (kW) — 조회기간 중 가장 마지막 분(分)의 시설합 PWI</li>
 *   <li>{@link #elceg} 전력량 (kWh) — 조회기간 누적 전력량 (PWQ 적산 버킷 차분 합)</li>
 *   <li>{@link #peakElpwr} 최대전력 (kW) — 집계단위 버킷별 시설합 PWI MAX 중 최댓값</li>
 *   <li>{@link #peakElpwrDtm} 최대전력 일시 — 최대전력이 발생한 버킷 시작 시각 (집계단위 입도)</li>
 * </ul>
 *
 * <p>전력 측정 대상은 운영시설 + 그 모든 하위 시설(재귀 롤업)의 모든 계측기 PWI/PWQ 태그 분(分)별 합산
 * (시설별사용량-2번섹션 PLAN1 §확정된 설계 결정 1·2). 데이터 부재(전 구간 BAD / 태그 없음) 시 4지표 모두
 * {@code null} — 0kW 와 "측정 없음" 을 구분한다 (PLAN1 §확정된 설계 결정 3).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 단순 조회 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합, {@code FacilityOperatingStatusDto}
 * 선례 동형).</p>
 *
 * <p>{@link #peakElpwr} 와 {@code PeakEnergyTrendService.billingPeak}(시스템 전역 12개월 분합 MAX) 의 의미 경계:
 * 본 {@code peakElpwr} 은 시설 단위 + 조회기간 한정 + 집계단위 버킷 MAX 로 발생 시각({@link #peakElpwrDtm}) 을
 * 동반한다 (시설별사용량-2번섹션 PLAN1 §신규 용어).</p>
 */
@Getter
@Schema(description = "시설별 사용량 2번섹션 — 운영시설 전력 사용량")
public class FacilityEnergyUsageDto {

    @Schema(description = "시설 ID", example = "fa-xxx-xxx")
    private String facilityId;

    @Schema(description = "시설명", example = "1단계 송수동")
    private String facilityNm;

    @Schema(description = "순시전력 (kW) — 조회기간 중 마지막 분의 시설합 PWI. 데이터 부재 시 null",
            example = "120.5000")
    private BigDecimal elpwr;

    @Schema(description = "전력량 (kWh) — 조회기간 누적 전력량 (PWQ 적산 버킷 차분 합). 데이터 부재 시 null",
            example = "2840.0000")
    private BigDecimal elceg;

    @Schema(description = "최대전력 (kW) — 집계단위 버킷별 시설합 PWI MAX 중 최댓값. 데이터 부재 시 null",
            example = "150.0000")
    private BigDecimal peakElpwr;

    @Schema(description = "최대전력 일시 — 최대전력이 발생한 버킷 시작 시각 (집계단위 입도). 데이터 부재 시 null",
            example = "2026-01-15 14:00:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime peakElpwrDtm;

    private FacilityEnergyUsageDto() {
    }

    /**
     * 운영시설 전력 사용량 응답 DTO 정적 팩토리.
     *
     * @param facilityId   시설 ID
     * @param facilityNm   시설명
     * @param elpwr        순시전력 (kW, 부재 시 null)
     * @param elceg        전력량 (kWh, 부재 시 null)
     * @param peakElpwr    최대전력 (kW, 부재 시 null)
     * @param peakElpwrDtm 최대전력 발생 일시 (부재 시 null)
     * @return 구성된 응답 DTO
     */
    public static FacilityEnergyUsageDto of(
            String facilityId,
            String facilityNm,
            BigDecimal elpwr,
            BigDecimal elceg,
            BigDecimal peakElpwr,
            LocalDateTime peakElpwrDtm) {
        FacilityEnergyUsageDto dto = new FacilityEnergyUsageDto();
        dto.facilityId = facilityId;
        dto.facilityNm = facilityNm;
        dto.elpwr = elpwr;
        dto.elceg = elceg;
        dto.peakElpwr = peakElpwr;
        dto.peakElpwrDtm = peakElpwrDtm;
        return dto;
    }
}
