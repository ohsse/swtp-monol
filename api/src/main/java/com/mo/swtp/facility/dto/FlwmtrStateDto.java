package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 유량계(FLWMTR) 1대의 FRI 유량·PRI 압력 최신 측정값 결합 응답 DTO.
 *
 * <p>FRI(유량)·PRI(압력) 측정 유형이 다르므로 단일 DTO 내에 별도 4컬럼씩 분리 보유한다
 * (송수펌프제어분석-3번섹션 PLAN1 §가정 결정 — FRI·PRI 분리 필드 채택, 8 필드).</p>
 *
 * <p>최신 측정값이 1시간 윈도우 내 없는 태그는 해당 필드가 NULL 로 반환된다. qualityCd 가 BAD·UNCERTAIN
 * 인 경우에도 raw/corr 값은 그대로 응답 (ANALYZE1 안건 10 결정 — frontend 가 qualityCd 보고 표시 분기).</p>
 */
@Getter
@Schema(description = "유량계(FLWMTR) 실시간 상태 — FRI 유량 + PRI 압력")
public class FlwmtrStateDto {

    @Schema(description = "계측기 ID", example = "in-xxx-xxx")
    private String instrumentId;

    @Schema(description = "계측기명", example = "유량계1")
    private String instrumentNm;

    @Schema(description = "FRI 원본 유량 측정값 (m³/h)", example = "245.3")
    private BigDecimal flwrtRawVal;

    @Schema(description = "FRI 보정 유량 측정값 (m³/h, NULL 허용)", example = "245.5")
    private BigDecimal flwrtCorrVal;

    @Schema(description = "FRI 수집 일시", example = "2026-05-13 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flwrtAcqDtm;

    @Schema(description = "FRI SCADA 품질 코드", implementation = QualityCode.class)
    private QualityCode flwrtQualityCd;

    @Schema(description = "PRI 원본 압력 측정값 (kgf/cm²)", example = "2.45")
    private BigDecimal prsrRawVal;

    @Schema(description = "PRI 보정 압력 측정값 (kgf/cm², NULL 허용)", example = "2.46")
    private BigDecimal prsrCorrVal;

    @Schema(description = "PRI 수집 일시", example = "2026-05-13 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime prsrAcqDtm;

    @Schema(description = "PRI SCADA 품질 코드", implementation = QualityCode.class)
    private QualityCode prsrQualityCd;

    private FlwmtrStateDto() {
    }

    /**
     * 유량계 상태 응답 DTO 정적 팩토리.
     */
    public static FlwmtrStateDto of(
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
        FlwmtrStateDto dto = new FlwmtrStateDto();
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
