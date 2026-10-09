package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.QTagPrediction;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * {@link PeakPredcCustomRepository} 구현 — Querydsl GROUP BY + HAVING.
 *
 * <p>전력피크분석-2번섹션 PLAN1 (2026-06-05) — 10번 섹션 {@code TagPredcRangeCustomRepositoryImpl}
 * (시점 범위 시계열 전체 조회) 와 동일 엔티티 ({@code predc_1m_h}) 를 가리키나, 본 사이클은 분별 예측합이 목표
 * 피크를 초과하는 최근접 시각 1건만 산정하므로 {@code GROUP BY predc_dtm HAVING SUM(predc_val) > target} +
 * {@code ORDER BY predc_dtm ASC LIMIT 1} Querydsl 빌더로 분리한다. ANALYZE1 안건 3 의 native 초안을 Querydsl
 * 로 정련 — 단일 레벨 GROUP BY+HAVING 은 Querydsl 표현이 가능하고 코드베이스 선례
 * ({@code TagPredcRangeCustomRepositoryImpl}) 와 정합한다 (사용자 결정 2026-06-05).</p>
 *
 * <p>인덱스 정합성: {@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)} 활용. {@code predc_dtm} 범위 조건
 * ({@code now..horizonEnd}) 으로 월 RANGE 파티션 프루닝 강제 — {@code horizonEnd} 상한으로 스캔 파티션 1~2개 제한.</p>
 *
 * <p>{@code §2.5} 면책 영역 (DB 쿼리 빌더·튜닝 코드) — 인용 근거:
 * {@code .claude/rules/db/query-tuning.md §2 p6spy 슬로우 쿼리 활용} (EXPLAIN ANALYZE 결과 정합성 검증
 * 대상). 단일 메서드 본문 50줄 미만이나 Querydsl 직접 빌더·파티션 프루닝 의도 단일 흐름 보존이 목적이다.</p>
 */
@RequiredArgsConstructor
public class PeakPredcCustomRepositoryImpl implements PeakPredcCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public LocalDateTime findEarliestPredcDtmOverTarget(
            List<String> tagSrlNos, LocalDateTime now, LocalDateTime horizonEnd, BigDecimal targetPeakElpwr) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return null;
        }
        // §2.5 면책 (query-tuning.md §2) — 분별 예측합 GROUP BY + HAVING SUM > target, ASC LIMIT 1 단일 흐름 보존.
        // predc_dtm 범위(now..horizonEnd)로 월 RANGE 파티션 프루닝 강제 (horizonEnd 상한으로 스캔 파티션 제한).
        QTagPrediction prediction = QTagPrediction.tagPrediction;
        return queryFactory
                .select(prediction.predcDtm)
                .from(prediction)
                .where(prediction.tagSrlNo.in(tagSrlNos)
                        .and(prediction.predcDtm.goe(now))
                        .and(prediction.predcDtm.lt(horizonEnd)))
                .groupBy(prediction.predcDtm)
                .having(prediction.predcVal.sum().gt(targetPeakElpwr))
                .orderBy(prediction.predcDtm.asc())
                .limit(1)
                .fetchFirst();
    }
}
