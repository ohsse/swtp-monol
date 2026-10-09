package com.mo.swtp.opt.repository;

import com.mo.swtp.common.util.JdbcTimestamps;
import com.mo.swtp.opt.dto.TagPredictionMatchDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * {@link TagPredictionCustomRepository} native SQL 구현.
 *
 * <p>송수펌프제어분석-7번섹션 PLAN1 §2 — {@code latest_meas} CTE + {@code CROSS JOIN LATERAL} 근접매칭
 * 단일 흐름. 활성 시설 태그별 "현황 최신 acq_dtm + 1시간" 에 가장 근접한 예측행 1건을 일괄 조회한다.</p>
 *
 * <p>인덱스 방향 혼재 의도 (PLAN1 §2 dba 참고):</p>
 * <ul>
 *   <li>{@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)} <b>ASC</b> —
 *       {@code BETWEEN target±windowMinutes} 대칭 범위 스캔 + {@code ABS(...)} 정렬, forward scan 필요</li>
 *   <li>{@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)} <b>DESC</b> —
 *       {@code DISTINCT ON} 최신 1건 단방향, Index Scan Backward + Sort 노드 부재</li>
 * </ul>
 * 목적 상이로 의도된 혼재이며, 단일 인덱스 방향으로 통일하면 한 쪽이 sort cost 부담.
 *
 * <p>LATERAL 동작 특성 (PLAN1 §2 dba 참고): {@code CROSS JOIN LATERAL} 은 {@code latest_meas} IN 절 일괄
 * 조회 후 태그별 1회 인덱스 스캔 (N=태그수) — JPA 루프 N+1 과 구별되는 정상 동작 (Nested Loop / Hash Join
 * 전략은 옵티마이저 선택).</p>
 *
 * <p>{@code §2.5} 면책 영역 (DB 쿼리 빌더·튜닝 코드) — 인용 근거:
 * {@code .claude/rules/db/query-tuning.md §2 p6spy 슬로우 쿼리 활용}
 * (EXPLAIN ANALYZE 결과 정합성 검증 대상). 단일 메서드 본문은 50줄을 초과할 수 있으나 CTE + LATERAL
 * 단일 흐름 분해 시 검증 의도 파편화 위험으로 면책 적용 ({@code coding-discipline.md §2.5}).</p>
 *
 * <p>윈도우 외부 결측 처리: 윈도우 내 예측행이 없는 태그는 LATERAL 미반환 → 응답 List 에 포함되지 않는다.
 * Service 가 {@code Map<tagSrlNo, _>} 그룹화 시 결측 태그는 자동으로 null 매핑 (PLAN1 가정 #2).</p>
 */
@Repository
public class TagPredictionCustomRepositoryImpl implements TagPredictionCustomRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<TagPredictionMatchDto> findNearestByTagSrlNos(List<String> tagSrlNos, int windowMinutes) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — latest_meas CTE + CROSS JOIN LATERAL 근접매칭 단일 흐름 보존
        // 인덱스 방향 혼재 의도: predc ASC (대칭 범위 스캔) + rawdata DESC (DISTINCT ON 단방향)
        // LATERAL = 태그별 1회 인덱스 스캔 (N+1 아님 — 옵티마이저 Nested Loop / Hash Join 선택)
        List<Object[]> rows = em.createNativeQuery("""
                        WITH latest_meas AS (
                          SELECT DISTINCT ON (tag_srl_no) tag_srl_no, acq_dtm
                          FROM rawdata_1m_h
                          WHERE tag_srl_no IN (:tagSrlNos)
                            AND acq_dtm >= NOW() - INTERVAL '1 hour'
                          ORDER BY tag_srl_no, acq_dtm DESC
                        )
                        SELECT lm.tag_srl_no, p.predc_dtm, p.predc_val
                        FROM latest_meas lm
                        CROSS JOIN LATERAL (
                          SELECT predc_dtm, predc_val
                          FROM predc_1m_h
                          WHERE tag_srl_no = lm.tag_srl_no
                            AND predc_dtm BETWEEN (lm.acq_dtm + INTERVAL '1 hour')
                                                    - (:windowMinutes * INTERVAL '1 minute')
                                              AND (lm.acq_dtm + INTERVAL '1 hour')
                                                    + (:windowMinutes * INTERVAL '1 minute')
                          ORDER BY ABS(EXTRACT(EPOCH FROM (predc_dtm - (lm.acq_dtm + INTERVAL '1 hour'))))
                          LIMIT 1
                        ) p
                        """)
                .setParameter("tagSrlNos", tagSrlNos)
                .setParameter("windowMinutes", windowMinutes)
                .getResultList();
        return rows.stream()
                .map(r -> new TagPredictionMatchDto(
                        (String) r[0],
                        JdbcTimestamps.toLocalDateTime(r[1]),
                        (BigDecimal) r[2]
                ))
                .toList();
    }
}
