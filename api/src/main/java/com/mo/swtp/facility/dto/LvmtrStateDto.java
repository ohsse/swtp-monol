package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 배수지(DWT) 수위계 1대의 LEI 수위 최신 측정값 응답 DTO.
 *
 * <p>송수펌프제어분석 §4 — 배수지 DWT 현황 표출. 수위계의 측정 항목은 LEI(수위, m) 1종.
 * 표준 단어 {@code wtlv}(수위, water level — DOM_QTY_15_4) 와 정합. enum 코드 LEI ({@code TagMeasurementType})
 * 는 SCADA 인바운드 분류 코드이며 응답 DTO 변수명은 의미 단어 {@code wtlv} 로 표현
 * (계층 분리 — {@code flwrt}·{@code prsr}·{@code opng} 선례 동일).</p>
 *
 * <p>LVMTR 다수 결측 시 단건 필터 금지 — 전체 목록 + 각 측정값 {@code qualityCd} 노출 의무 (PLAN1 §가정 결정).
 * BAD/UNCERTAIN 수위계 인지 보장.</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "배수지 수위계 실시간 상태 — LEI 수위")
public class LvmtrStateDto {

    @Schema(description = "계측기 ID", example = "in-xxx-xxx")
    private String instrumentId;

    @Schema(description = "계측기명", example = "배수지수위계1")
    private String instrumentNm;

    @Schema(description = "LEI 원본 수위 측정값 (m)", example = "5.32")
    private BigDecimal wtlvRawVal;

    @Schema(description = "LEI 보정 수위 측정값 (m, NULL 허용)", example = "5.35")
    private BigDecimal wtlvCorrVal;

    @Schema(description = "LEI 수집 일시", example = "2026-05-14 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime wtlvAcqDtm;

    @Schema(description = "LEI SCADA 품질 코드", implementation = QualityCode.class)
    private QualityCode wtlvQualityCd;

    private LvmtrStateDto() {
    }

    /**
     * 수위계 상태 응답 DTO 정적 팩토리.
     */
    public static LvmtrStateDto of(
            String instrumentId,
            String instrumentNm,
            BigDecimal wtlvRawVal,
            BigDecimal wtlvCorrVal,
            LocalDateTime wtlvAcqDtm,
            QualityCode wtlvQualityCd) {
        LvmtrStateDto dto = new LvmtrStateDto();
        dto.instrumentId = instrumentId;
        dto.instrumentNm = instrumentNm;
        dto.wtlvRawVal = wtlvRawVal;
        dto.wtlvCorrVal = wtlvCorrVal;
        dto.wtlvAcqDtm = wtlvAcqDtm;
        dto.wtlvQualityCd = wtlvQualityCd;
        return dto;
    }
}
