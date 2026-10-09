package com.mo.swtp.instrument.dto;

import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import lombok.Getter;

/**
 * 송수펌프 제어이력 2번섹션 — AI 운영 현황 통계 응답 DTO (읽기 전용).
 *
 * <p>조회기간 동안의 제어 이력을 운전모드(AI/AI추천/AI분석)별로 카운트하고, 모드별 비율(모드 카운트 ÷ 전체
 * 카운트, %)을 차트·표로 표출한다. 전체 카운트는 운전모드가 부여된(AI 제어) 이력의 합이며, 수동 제어
 * ({@code ai_drvn_mod IS NULL}) 는 집계에서 제외된다 (제어이력 재도입 PLAN1 §구현 방향 Phase 3,
 * 이미지상 전체 = 3개 AI 모드 합).</p>
 *
 * <p>응답은 AI/AI_RECOMD/AI_ANLS <strong>3종을 항상 포함</strong>한다 — 카운트 0건 모드도 0·0.0% 로 표출
 * (이미지 AI분석 0/0% 정합). 읽기 전용 통계 응답이므로 {@code BaseAuditResponseDto} 미상속
 * ({@code .claude/rules/api-patterns.md §BaseAuditResponseDto 패턴 §적용 범위} — 요약·통계 응답 미적용 정합).</p>
 */
@Getter
@Schema(description = "송수펌프 AI 운영 현황 통계 응답 DTO — 2번섹션")
public class PumpCtrlStatDto {

    @Schema(description = "전체 제어 카운트 (AI 운전모드 부여 이력 합, 수동 제외)", example = "102")
    private final long totalCount;

    @ArraySchema(schema = @Schema(description = "운전모드별 통계 (AI/AI추천/AI분석 3종 항상 포함)",
            implementation = ModeStat.class))
    private final List<ModeStat> modeStats;

    private PumpCtrlStatDto(long totalCount, List<ModeStat> modeStats) {
        this.totalCount = totalCount;
        this.modeStats = modeStats;
    }

    /**
     * 전체 카운트 + 운전모드별 통계 목록으로 응답 DTO 를 생성한다.
     *
     * @param totalCount 전체 제어 카운트 (모드별 카운트 합)
     * @param modeStats  운전모드별 통계 목록 (AI/AI_RECOMD/AI_ANLS 3종)
     * @return 구성된 통계 응답 DTO
     */
    public static PumpCtrlStatDto of(long totalCount, List<ModeStat> modeStats) {
        return new PumpCtrlStatDto(totalCount, modeStats);
    }

    /**
     * 운전모드 단일 통계 — 모드 · 카운트 · 비율(%).
     *
     * <p>{@code rate} 는 모드 카운트 ÷ 전체 카운트 × 100 (HALF_UP, 소수 첫째 자리). 전체 카운트 0 시 0.0
     * (분모 0 방어).</p>
     */
    @Getter
    @Schema(description = "운전모드 단일 통계")
    public static class ModeStat {

        @Schema(description = "운전모드 (AI/AI추천/AI분석)", implementation = AiDrvnModeCode.class)
        private final AiDrvnModeCode aiDrvnMod;

        @Schema(description = "해당 모드 제어 카운트", example = "68")
        private final long count;

        @Schema(description = "비율 (% — 모드 카운트 ÷ 전체 카운트, 소수 첫째 자리)", example = "66.7")
        private final BigDecimal rate;

        private ModeStat(AiDrvnModeCode aiDrvnMod, long count, BigDecimal rate) {
            this.aiDrvnMod = aiDrvnMod;
            this.count = count;
            this.rate = rate;
        }

        /**
         * 운전모드 단일 통계 정적 팩토리.
         *
         * @param aiDrvnMod 운전모드
         * @param count     해당 모드 제어 카운트
         * @param rate      비율 (%)
         * @return 구성된 모드 통계
         */
        public static ModeStat of(AiDrvnModeCode aiDrvnMod, long count, BigDecimal rate) {
            return new ModeStat(aiDrvnMod, count, rate);
        }
    }
}
