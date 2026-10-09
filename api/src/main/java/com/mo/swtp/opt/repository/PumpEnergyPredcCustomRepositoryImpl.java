package com.mo.swtp.opt.repository;

import com.mo.swtp.common.util.JdbcTimestamps;
import com.mo.swtp.opt.dto.PredcEnergyBucketDto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link PumpEnergyPredcCustomRepository} 구현 — native SQL ({@code MAX(predc_val)-MIN(predc_val)} GROUP BY).
 *
 * <p>전력피크분석-4번섹션 PLAN1 (2026-06-05) — 펌프 PWQ 예측 적산값의 1시간 버킷 차분을 산정한다.
 * 실측 {@code rawdata_1m_h} 의 {@link com.mo.swtp.raw.repository.RawDataCustomRepositoryImpl#findEnergyDeltaBuckets}
 * 를 미러링하되 대상 테이블을 {@code predc_1m_h} 로 바꾸고 {@code quality_cd}·{@code corr_val} 조건을 제거한다
 * (예측 테이블에는 두 컬럼이 부재 — 전력피크분석-4번섹션 ANALYZE1 안건 3). 시간(hour) 고정 버킷이라
 * {@code date_trunc} 단위를 SQL 에 상수로 박는다 (가변 {@code :unit} 바인드 불요).</p>
 *
 * <p>버킷별 차분은 펌프(태그) 단위로 산정되며 — {@code SUM(MAX-MIN) != MAX(SUM)-MIN(SUM)} 이므로 펌프별로
 * 먼저 차분한 뒤 호출 Service 가 버킷별 시설 합산을 수행한다 (전력피크분석-4번섹션 ANALYZE1 안건 3 DBA 확인).</p>
 *
 * <p>{@code §2.5} 면책 영역 (DB 쿼리 빌더·튜닝 코드) — 인용 근거:
 * {@code .claude/rules/db/query-tuning.md §2 p6spy 슬로우 쿼리 활용} (EXPLAIN ANALYZE 결과 정합성 검증
 * 대상). 단일 메서드 본문 50줄 미만이나 native SQL 직접 작성·파티션 프루닝 의도 단일 흐름 보존이 목적이다.</p>
 */
public class PumpEnergyPredcCustomRepositoryImpl implements PumpEnergyPredcCustomRepository {

    @PersistenceContext
    private EntityManager em;

    @Override
    @SuppressWarnings("unchecked")
    public List<PredcEnergyBucketDto> findEnergyDeltaBuckets(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 예측 적산전력량 버킷 차분 MAX-MIN GROUP BY date_trunc('hour') 단일 흐름 보존.
        // predc_1m_h 는 quality_cd·corr_val 부재 → predc_val IS NOT NULL 단독 (rawdata 의 GOOD 필터·HLV 분기 미적용).
        // predc_dtm 범위로 월 RANGE 파티션 프루닝 강제 (idx_predc_1m_h_tag_time). 시간 고정 버킷이라 date_trunc 단위 상수.
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT tag_srl_no,
                               date_trunc('hour', predc_dtm) AS base_dtm,
                               MAX(predc_val) - MIN(predc_val) AS aggr_val
                        FROM predc_1m_h
                        WHERE tag_srl_no IN (:tagSrlNos)
                          AND predc_dtm >= :startDtm AND predc_dtm < :endDtm
                          AND predc_val IS NOT NULL
                        GROUP BY tag_srl_no, date_trunc('hour', predc_dtm)
                        ORDER BY tag_srl_no, base_dtm
                        """)
                .setParameter("tagSrlNos", tagSrlNos)
                .setParameter("startDtm", startDtm)
                .setParameter("endDtm", endDtm)
                .getResultList();
        return rows.stream()
                .map(r -> new PredcEnergyBucketDto(
                        (String) r[0],
                        JdbcTimestamps.toLocalDateTime(r[1]),
                        (BigDecimal) r[2]
                ))
                .toList();
    }
}
