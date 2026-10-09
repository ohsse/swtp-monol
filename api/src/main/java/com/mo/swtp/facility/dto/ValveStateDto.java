package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 배수지(DWT) 밸브 1대의 VOI 개도 최신 측정값 응답 DTO.
 *
 * <p>송수펌프제어분석 §4 — 배수지 DWT 현황 표출. 밸브의 측정 항목은 VOI(개도율, %) 1종.
 * 표준 단어 {@code opng}(개도, opening — DOM_QTY_15_4) 와 정합 (송수펌프제어분석-4번섹션 ANALYZE1 신규 등록).
 * enum 코드 VOI ({@code TagMeasurementType}) 는 SCADA 인바운드 분류 코드이며 응답 DTO 변수명은 의미 단어
 * {@code opng} 로 표현 (계층 분리 — {@code flwrt}·{@code prsr}·{@code wtlv} 선례 동일).</p>
 *
 * <p>최신 측정값이 1시간 윈도우 내 없는 태그는 해당 필드가 NULL 로 반환된다. {@code qualityCd} 가 BAD·UNCERTAIN
 * 인 경우에도 raw/corr 값은 그대로 응답.</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "배수지 밸브 실시간 상태 — VOI 개도")
public class ValveStateDto {

    @Schema(description = "계측기 ID", example = "in-xxx-xxx")
    private String instrumentId;

    @Schema(description = "계측기명", example = "유입밸브1")
    private String instrumentNm;

    @Schema(description = "VOI 원본 개도 측정값 (%)", example = "75.2")
    private BigDecimal opngRawVal;

    @Schema(description = "VOI 보정 개도 측정값 (%, NULL 허용)", example = "75.4")
    private BigDecimal opngCorrVal;

    @Schema(description = "VOI 수집 일시", example = "2026-05-14 10:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime opngAcqDtm;

    @Schema(description = "VOI SCADA 품질 코드", implementation = QualityCode.class)
    private QualityCode opngQualityCd;

    private ValveStateDto() {
    }

    /**
     * 밸브 상태 응답 DTO 정적 팩토리.
     */
    public static ValveStateDto of(
            String instrumentId,
            String instrumentNm,
            BigDecimal opngRawVal,
            BigDecimal opngCorrVal,
            LocalDateTime opngAcqDtm,
            QualityCode opngQualityCd) {
        ValveStateDto dto = new ValveStateDto();
        dto.instrumentId = instrumentId;
        dto.instrumentNm = instrumentNm;
        dto.opngRawVal = opngRawVal;
        dto.opngCorrVal = opngCorrVal;
        dto.opngAcqDtm = opngAcqDtm;
        dto.opngQualityCd = opngQualityCd;
        return dto;
    }
}
