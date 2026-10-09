package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 배수지(DWT) 유입 유량계 1대의 FRI 유량 + PRI 압력 최신 측정값 응답 DTO.
 *
 * <p>송수펌프제어분석 §4 — 배수지 DWT 현황 표출. 유입 유량계는 FRI(유량) 와 PRI(압력) 두 측정 항목을
 * 함께 보유하므로 4 + 4 = 8 필드를 단일 DTO 에 분리 보유한다 (§3 의 {@link FlwmtrStateDto} 와 동일 패턴).
 * 유출 유량계는 PRI 미보유 — {@link OutletFlwmtrStateDto} 로 분리.</p>
 *
 * <p>최신 측정값이 1시간 윈도우 내 없는 태그는 해당 필드가 NULL 로 반환된다. {@code qualityCd} 가 BAD·UNCERTAIN
 * 인 경우에도 raw/corr 값은 그대로 응답 (PLAN1 §가정 결정 — frontend 가 qualityCd 보고 표시 분기).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "배수지 유입 유량계 실시간 상태 — FRI 유량 + PRI 압력")
public class InletFlwmtrStateDto {

    @Schema(description = "계측기 ID", example = "in-xxx-xxx")
    private String instrumentId;

    @Schema(description = "계측기명", example = "유입유량계1")
    private String instrumentNm;

    @Schema(description = "FRI 원본 유량 측정값 (m³/h)", example = "245.3")
    private BigDecimal flwrtRawVal;

    @Schema(description = "FRI 보정 유량 측정값 (m³/h, NULL 허용)", example = "245.5")
    private BigDecimal flwrtCorrVal;

    @Schema(description = "FRI 수집 일시", example = "2026-05-14 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flwrtAcqDtm;

    @Schema(description = "FRI SCADA 품질 코드", implementation = QualityCode.class)
    private QualityCode flwrtQualityCd;

    @Schema(description = "PRI 원본 압력 측정값 (kgf/cm²)", example = "2.45")
    private BigDecimal prsrRawVal;

    @Schema(description = "PRI 보정 압력 측정값 (kgf/cm², NULL 허용)", example = "2.46")
    private BigDecimal prsrCorrVal;

    @Schema(description = "PRI 수집 일시", example = "2026-05-14 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime prsrAcqDtm;

    @Schema(description = "PRI SCADA 품질 코드", implementation = QualityCode.class)
    private QualityCode prsrQualityCd;

    private InletFlwmtrStateDto() {
    }

    /**
     * 유입 유량계 상태 응답 DTO 정적 팩토리.
     */
    public static InletFlwmtrStateDto of(
            String instrumentId,
            String instrumentNm,
            BigDecimal flwrtRawVal,
            BigDecimal flwrtCorrVal,
            LocalDateTime flwrtAcqDtm,
            QualityCode flwrtQualityCd,
            BigDecimal prsrRawVal,
            BigDecimal prsrCorrVal,
            LocalDateTime prsrAcqDtm,
            QualityCode prsrQualityCd) {
        InletFlwmtrStateDto dto = new InletFlwmtrStateDto();
        dto.instrumentId = instrumentId;
        dto.instrumentNm = instrumentNm;
        dto.flwrtRawVal = flwrtRawVal;
        dto.flwrtCorrVal = flwrtCorrVal;
        dto.flwrtAcqDtm = flwrtAcqDtm;
        dto.flwrtQualityCd = flwrtQualityCd;
        dto.prsrRawVal = prsrRawVal;
        dto.prsrCorrVal = prsrCorrVal;
        dto.prsrAcqDtm = prsrAcqDtm;
        dto.prsrQualityCd = prsrQualityCd;
        return dto;
    }
}
