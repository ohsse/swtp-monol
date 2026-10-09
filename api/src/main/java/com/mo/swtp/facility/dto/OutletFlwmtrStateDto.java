package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 배수지(DWT) 유출 유량계 1대의 FRI 유량 최신 측정값 응답 DTO.
 *
 * <p>송수펌프제어분석 §4 — 배수지 DWT 현황 표출. 유출 유량계는 PRI(압력) 미보유, FRI(유량) 4 필드만 노출한다
 * (PLAN1 §응답 DTO 구조). 유입 유량계는 PRI 포함 — {@link InletFlwmtrStateDto} 로 분리.</p>
 *
 * <p>최신 측정값이 1시간 윈도우 내 없는 태그는 해당 필드가 NULL 로 반환된다. {@code qualityCd} 가 BAD·UNCERTAIN
 * 인 경우에도 raw/corr 값은 그대로 응답 (PLAN1 §가정 결정 — frontend 가 qualityCd 보고 표시 분기).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "배수지 유출 유량계 실시간 상태 — FRI 유량")
public class OutletFlwmtrStateDto {

    @Schema(description = "계측기 ID", example = "in-xxx-xxx")
    private String instrumentId;

    @Schema(description = "계측기명", example = "유출유량계1")
    private String instrumentNm;

    @Schema(description = "FRI 원본 유량 측정값 (m³/h)", example = "180.7")
    private BigDecimal flwrtRawVal;

    @Schema(description = "FRI 보정 유량 측정값 (m³/h, NULL 허용)", example = "180.9")
    private BigDecimal flwrtCorrVal;

    @Schema(description = "FRI 수집 일시", example = "2026-05-14 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flwrtAcqDtm;

    @Schema(description = "FRI SCADA 품질 코드", implementation = QualityCode.class)
    private QualityCode flwrtQualityCd;

    private OutletFlwmtrStateDto() {
    }

    /**
     * 유출 유량계 상태 응답 DTO 정적 팩토리.
     */
    public static OutletFlwmtrStateDto of(
            String instrumentId,
            String instrumentNm,
            BigDecimal flwrtRawVal,
            BigDecimal flwrtCorrVal,
            LocalDateTime flwrtAcqDtm,
            QualityCode flwrtQualityCd) {
        OutletFlwmtrStateDto dto = new OutletFlwmtrStateDto();
        dto.instrumentId = instrumentId;
        dto.instrumentNm = instrumentNm;
        dto.flwrtRawVal = flwrtRawVal;
        dto.flwrtCorrVal = flwrtCorrVal;
        dto.flwrtAcqDtm = flwrtAcqDtm;
        dto.flwrtQualityCd = flwrtQualityCd;
        return dto;
    }
}
