package com.mo.swtp.opt.repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 전력피크 예상시간 커스텀 조회 인터페이스 (전력피크분석 2번섹션 지표 ④).
 *
 * <p>전력피크분석-2번섹션 PLAN1 (2026-06-05) — PWI 예측 태그들의 분(分)별 예측값 합산이 목표 피크 전력을
 * 초과하는 가장 이른 미래 시각을 조회한다. 10번 섹션 {@link TagPredcRangeCustomRepository} (시점 범위 시계열
 * 전체 조회) 와 의도가 다르다 — 본 메서드는 {@code GROUP BY predc_dtm HAVING SUM(predc_val) > target} +
 * {@code ORDER BY predc_dtm ASC LIMIT 1} 로 임계 초과 최근접 시각 1건만 반환한다. 사이클 간 자산 자동 원용
 * 금지 정합 — 별도 Repository 로 분리한다 ({@code coding-discipline.md §3} 정밀한 수정).</p>
 *
 * <p>시계열 → 마스터 FK 금지 정책 ({@code .claude/rules/db/partitioning-and-retention.md §1}) 정합 —
 * {@code tag_srl_no} 는 {@code tag_m.tag_srl_no} 의 논리 참조이며 마스터 결합은 호출 Service 가 조합한다.</p>
 */
public interface PeakPredcCustomRepository {

    /**
     * PWI 예측 태그 합산이 목표 피크를 초과하는 가장 이른 미래 시각을 조회한다.
     *
     * <p>조건: {@code tag_srl_no IN :tagSrlNos AND predc_dtm >= :now AND predc_dtm < :horizonEnd}.
     * 분(分)별 그룹 합산 {@code SUM(predc_val)} 이 {@code :targetPeakElpwr} 를 초과하는 그룹 중
     * {@code predc_dtm} 오름차순 첫 1건을 반환한다 (현재시간에서 가장 가까운 미래 시각). 결측 정책:
     * 그 시각에 존재하는 예측값만 합산(부분합 허용) — 총순시전력 합산과 동일 정책.</p>
     *
     * <p>{@code predc_dtm} 범위 조건으로 월 RANGE 파티션 프루닝 강제 — {@code horizonEnd} 상한으로 스캔 파티션을
     * 1~2개로 제한한다 ({@code predc_1m_h} 3년 보존 36파티션 풀스캔 방지, DBA 블로커 해소).</p>
     *
     * @param tagSrlNos       PWI 예측 태그 시리얼번호 목록 (빈 리스트 시 {@code null} 반환)
     * @param now             현재 일시 (조회 하한, inclusive)
     * @param horizonEnd      예측 지평 상한 (exclusive, 보통 now + {@code opt.peak.predc-horizon-hours})
     * @param targetPeakElpwr 목표 피크 전력값 (이 값 초과 시각만 매칭)
     * @return 목표 초과 최근접 미래 시각 (없으면 {@code null} — "없음")
     */
    LocalDateTime findEarliestPredcDtmOverTarget(
            List<String> tagSrlNos, LocalDateTime now, LocalDateTime horizonEnd, BigDecimal targetPeakElpwr);
}
