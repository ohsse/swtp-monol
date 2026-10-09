package com.mo.swtp.facility.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mo.swtp.instrument.domain.PumpOprtngType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import lombok.Getter;

/**
 * 펌프(PUMP) 1대의 OPS AI 예측 가동상태 + 정적 조작유형 응답 DTO — 송수펌프제어분석 7번 섹션.
 *
 * <p>{@code oprtngType} 은 펌프의 물리적 설계값 (AI 운전 모드와 무관) — 마스터 데이터로 정적이므로
 * 예측 측정값과 별도로 노출한다 (섹션 3 {@link PumpStateDto} 패턴 정합).</p>
 *
 * <p>{@code predcIsRunning} 은 OPS 태그의 예측 {@code predc_val} (1.0=ON / 0.0=OFF) 을 Boolean 변환한 값으로,
 * 윈도우 내 예측행 부재 또는 예측값 NULL 시 null 로 반환된다. 섹션 3 의 {@code isRunning} 미러 + {@code predc}
 * 접두어로 예측임을 명시 (PLAN1 §5 ANALYZE1 가정 #4 결정 — 부동소수 코드값 오인 방지 Boolean 타입).</p>
 *
 * <p>{@code quality_cd} 컬럼 부재 — 예측값에는 SCADA QUALITY 개념이 없음 (PLAN1 §1 ANALYZE1 안건 5).</p>
 *
 * <p>{@code BaseAuditResponseDto} 미상속 — 실시간/예측 통지성 응답 분류
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 적용 범위} 정합).</p>
 */
@Getter
@Schema(description = "펌프(PUMP) 예측 데이터 — OPS 예측 가동상태 + 정적 조작유형")
public class PumpPredictionDto {

    @Schema(description = "계측기 ID", example = "in-xxx-xxx")
    private String instrumentId;

    @Schema(description = "계측기명", example = "송수펌프1")
    private String instrumentNm;

    @Schema(description = "펌프 물리 조작 가능 유형 (AI 운전 모드와 무관)",
            implementation = PumpOprtngType.class)
    private PumpOprtngType oprtngType;

    @Schema(description = "예측 가동상태(미래 시점) — true=ON·false=OFF·null=결측", example = "true")
    private Boolean predcIsRunning;

    @Schema(description = "OPS 예측 대상 일시 (근접매칭 결과의 실제 시각, 결측 시 NULL)",
            example = "2026-05-18 11:30:00")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime predcDtm;

    private PumpPredictionDto() {
    }

    /**
     * 펌프 예측 응답 DTO 정적 팩토리.
     */
    public static PumpPredictionDto of(
            String instrumentId,
            String instrumentNm,
            PumpOprtngType oprtngType,
            Boolean predcIsRunning,
            LocalDateTime predcDtm) {
        PumpPredictionDto dto = new PumpPredictionDto();
        dto.instrumentId = instrumentId;
        dto.instrumentNm = instrumentNm;
        dto.oprtngType = oprtngType;
        dto.predcIsRunning = predcIsRunning;
        dto.predcDtm = predcDtm;
        return dto;
    }
}
