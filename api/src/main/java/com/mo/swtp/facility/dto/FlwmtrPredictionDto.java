package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 유량계(FLWMTR) 1대의 FRI 유량·PRI 압력 AI 예측값 결합 응답 DTO — 송수펌프제어분석 7번 섹션.
 *
 * <p>FRI(유량)·PRI(압력) 측정 유형이 다르므로 단일 DTO 내에 별도 2컬럼씩 분리 보유 (예측값 + 예측 대상 시각).
 * 섹션 3 의 {@link FlwmtrStateDto} 가 원본/보정/품질 4컬럼을 보유하는 것과 달리, 본 DTO 는 예측 단일값만
 * 보유한다 — {@code quality_cd} 제외 결정 (PLAN1 §1 ANALYZE1 안건 5).</p>
 *
 * <p>윈도우 내 예측행이 없는 태그는 해당 필드가 NULL 로 반환된다.</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간/예측 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "유량계(FLWMTR) 예측 데이터 — FRI 유량 + PRI 압력 예측값")
public class FlwmtrPredictionDto {

    @Schema(description = "계측기 ID", example = "in-xxx-xxx")
    private String instrumentId;

    @Schema(description = "계측기명", example = "유량계1")
    private String instrumentNm;

    @Schema(description = "FRI 예측 유량 측정값 (m³/h, 결측 시 NULL)", example = "248.7")
    private BigDecimal flwrtPredcVal;

    @Schema(description = "FRI 예측 대상 일시 (근접매칭 결과의 실제 시각, 결측 시 NULL)",
            example = "2026-05-18 11:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime flwrtPredcDtm;

    @Schema(description = "PRI 예측 압력 측정값 (kgf/cm², 결측 시 NULL)", example = "2.48")
    private BigDecimal prsrPredcVal;

    @Schema(description = "PRI 예측 대상 일시 (근접매칭 결과의 실제 시각, 결측 시 NULL)",
            example = "2026-05-18 11:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime prsrPredcDtm;

    private FlwmtrPredictionDto() {
    }

    /**
     * 유량계 예측 응답 DTO 정적 팩토리.
     */
    public static FlwmtrPredictionDto of(
            String instrumentId,
            String instrumentNm,
            BigDecimal flwrtPredcVal,
            LocalDateTime flwrtPredcDtm,
            BigDecimal prsrPredcVal,
            LocalDateTime prsrPredcDtm) {
        FlwmtrPredictionDto dto = new FlwmtrPredictionDto();
        dto.instrumentId = instrumentId;
        dto.instrumentNm = instrumentNm;
        dto.flwrtPredcVal = flwrtPredcVal;
        dto.flwrtPredcDtm = flwrtPredcDtm;
        dto.prsrPredcVal = prsrPredcVal;
        dto.prsrPredcDtm = prsrPredcDtm;
        return dto;
    }
}
