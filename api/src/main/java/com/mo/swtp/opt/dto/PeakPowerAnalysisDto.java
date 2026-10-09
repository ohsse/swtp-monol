package com.mo.swtp.opt.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 전력피크분석 2·3번섹션 공용 5지표 조회 응답 DTO.
 *
 * <p>2번섹션 4지표(총순시전력·목표피크전력·요금적용전력피크·전력피크예상시간) + 3번섹션 주요내역 신규
 * 1지표(송수펌프 순시전력)를 단일 응답으로 구성한다. 여러 소스 ({@code rawdata_1m_h} 최신값·
 * {@code opt_peak_target_p}·{@code rawdata_1m_h} 12개월·{@code predc_1m_h}) 를 조합한 합성 뷰이므로
 * {@code BaseAuditResponseDto} 를 상속하지 않는다 ({@code FacilityOperatingStatusDto}·{@code PumpStateDto}
 * 통지/집계 응답 선례 — api-patterns.md §BaseAuditResponseDto 패턴 적용 외).</p>
 *
 * <p>{@code pumpElpwr}(송수펌프 순시전력)는 {@code totalElpwr}(총순시전력)의 부분집합으로, 펌프
 * ({@code equip_type_cd='PUMP'}) 매핑 PWI 태그만 합산한다 (전력피크분석-3번섹션 ANALYZE1, 2026-06-05).</p>
 *
 * <p>{@code predcPeakDtm} 은 {@code null} 일 때 화면에 "없음" 으로 표출된다 (목표 미설정·예측 데이터 부재·
 * 임계 초과 시각 부재). 초 단위 직렬화 ({@code BaseEntity.rgstrDtm} 동형 — api-patterns.md §직렬화 정책).</p>
 */
@Getter
@Schema(description = "전력피크분석 2·3번섹션 공용 5지표 조회 응답 DTO")
public class PeakPowerAnalysisDto {

    @Schema(description = "총순시전력 — 전체 PWI 태그 최신값 GOOD 합산 (kW)", example = "812.5000")
    private BigDecimal totalElpwr;

    @Schema(description = "송수펌프 순시전력 — 펌프(equip_type_cd='PUMP') 매핑 PWI 태그 최신값 GOOD 합산 (kW, On/Off 무관). "
            + "총순시전력의 부분집합. 운전현황 On펌프 합산과 혼동 주의 — 가동 여부 무관 전체 펌프 합산이며 대기 전력 포함 가능",
            example = "558.0000")
    private BigDecimal pumpElpwr;

    @Schema(description = "목표피크전력 — 1번섹션 설정 목표값 (kW, 0 = 미설정)", example = "900.0000")
    private BigDecimal targetPeakElpwr;

    @Schema(description = "요금적용전력피크 — 최근 12개월 분단위 PWI 합산값의 MAX (kW)", example = "1024.0000")
    private BigDecimal billingPeakElpwr;

    @Schema(description = "전력피크예상시간 — 예측 PWI 합이 목표 초과하는 최근접 미래 시각 (null = 없음)",
            example = "2026-06-05 14:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime predcPeakDtm;

    private PeakPowerAnalysisDto() {
    }

    /**
     * 5지표 응답 DTO 를 생성한다.
     *
     * @param totalElpwr       총순시전력 (kW)
     * @param pumpElpwr        송수펌프 순시전력 (kW, 펌프 매핑 PWI GOOD 합산, On/Off 무관)
     * @param targetPeakElpwr  목표피크전력 (kW, 0 = 미설정)
     * @param billingPeakElpwr 요금적용전력피크 (kW)
     * @param predcPeakDtm     전력피크예상시간 (null = 없음)
     * @return 5지표 응답 DTO
     */
    public static PeakPowerAnalysisDto of(
            BigDecimal totalElpwr,
            BigDecimal pumpElpwr,
            BigDecimal targetPeakElpwr,
            BigDecimal billingPeakElpwr,
            LocalDateTime predcPeakDtm) {
        PeakPowerAnalysisDto dto = new PeakPowerAnalysisDto();
        dto.totalElpwr = totalElpwr;
        dto.pumpElpwr = pumpElpwr;
        dto.targetPeakElpwr = targetPeakElpwr;
        dto.billingPeakElpwr = billingPeakElpwr;
        dto.predcPeakDtm = predcPeakDtm;
        return dto;
    }
}
