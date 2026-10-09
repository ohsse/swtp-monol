package com.mo.swtp.opt.repository;

import com.mo.swtp.common.util.JdbcTimestamps;
import com.mo.swtp.opt.dto.TagPredcLatestDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@link TagPredcLatestCustomRepository} 구현 — PostgreSQL DISTINCT ON + 1시간 윈도우 파티션 프루닝.
 *
 * <p>운전현황분석-9번섹션 PLAN1 (2026-05-21) — 4번 섹션 {@code RawDataCustomRepositoryImpl.findLatestByTagSrlNos}
 * 동형 패턴 (단일 native SQL + DISTINCT ON + {@code NOW() - INTERVAL '1 hour'} 하한). 데이터 소스만
 * {@code rawdata_1m_h} → {@code predc_1m_h}, 파티션 키 {@code acq_dtm} → {@code predc_dtm}, 컬럼은
 * {@code raw_val}·{@code corr_val}·{@code quality_cd} 3개 제거 후 {@code predc_val} 단일.</p>
 *
 * <p>인덱스 정합성: {@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)} 활용 → Index Scan Backward +
 * Sort 노드 부재. {@code predc_dtm >= NOW() - INTERVAL '1 hour'} 하한으로 월 RANGE 파티션 프루닝 강제
 * (현재 시점 1개 파티션만 스캔, PLAN1 §Repository 신규 §SQL).</p>
 *
 * <p>{@code §2.5} 면책 영역 (DB 쿼리 빌더·튜닝 코드) — 인용 근거:
 * {@code .claude/rules/db/query-tuning.md §2 p6spy 슬로우 쿼리 활용} (EXPLAIN ANALYZE 결과 정합성 검증
 * 대상). PostgreSQL DISTINCT ON 절은 Querydsl 표현 불가 + 파티션 프루닝 의도 단일 흐름 보존이 목적이다.</p>
 */
public class TagPredcLatestCustomRepositoryImpl implements TagPredcLatestCustomRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<TagPredcLatestDto> findLatestByTagSrlNos(List<String> tagSrlNos) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — DISTINCT ON + 1시간 윈도우 파티션 프루닝 단일 흐름 보존
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT DISTINCT ON (tag_srl_no)
                               tag_srl_no, predc_dtm, predc_val
                        FROM predc_1m_h
                        WHERE tag_srl_no IN (:tagSrlNos)
                          AND predc_dtm >= NOW() - INTERVAL '1 hour'
                        ORDER BY tag_srl_no, predc_dtm DESC
                        """)
                .setParameter("tagSrlNos", tagSrlNos)
                .getResultList();
        return rows.stream()
                .map(r -> new TagPredcLatestDto(
                        (String) r[0],
                        JdbcTimestamps.toLocalDateTime(r[1]),
                        (BigDecimal) r[2]
                ))
                .toList();
    }
}
