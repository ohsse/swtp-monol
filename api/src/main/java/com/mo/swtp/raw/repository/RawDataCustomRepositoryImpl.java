package com.mo.swtp.raw.repository;

import com.mo.swtp.common.util.JdbcTimestamps;
import com.mo.swtp.raw.domain.QRawData;
import com.mo.swtp.raw.domain.enumtype.QualityCode;
import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.dto.RawDataBucketPeakDto;
import com.mo.swtp.raw.dto.RawDataBucketSumDto;
import com.mo.swtp.raw.dto.RawDataFacilitySumDto;
import com.mo.swtp.raw.dto.RawDataInstrumentSumDto;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.dto.RawDataOnStateDto;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;

/**
 * {@link RawDataCustomRepository} 구현 — native SQL + Querydsl 혼합.
 *
 * <p>송수펌프제어분석-3번섹션 PLAN1 (2026-05-13) — PostgreSQL DISTINCT ON + 1시간 윈도우 파티션 프루닝으로
 * 재작성. 2번 섹션 자산 (Querydsl 서브쿼리 + {@code acqDtmFrom} 외부 주입) 일괄 폐기 후 단일 native SQL
 * 흐름으로 정렬.</p>
 *
 * <p>운전현황분석-5번섹션 PLAN1 (2026-05-21) — 시점 범위 시계열 조회 메서드
 * ({@link #findByTagSrlNosAndDtmRange}) 를 Querydsl 로 추가. DISTINCT ON 단일값 조회는 native, 일반 범위
 * 시계열 조회는 Querydsl 로 역할 분리.</p>
 *
 * <p>인덱스 정합성: {@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)} 활용 →
 * Index Scan Backward + Sort 노드 부재 (ANALYZE1 안건 5). {@code acq_dtm >= NOW() - INTERVAL '1 hour'}
 * 하한으로 월 RANGE 파티션 프루닝 강제 (ANALYZE1 안건 6).</p>
 *
 * <p>{@code §2.5} 면책 영역 (DB 쿼리 빌더·튜닝 코드) — 인용 근거:
 * {@code .claude/rules/db/query-tuning.md §2 p6spy 슬로우 쿼리 활용} (EXPLAIN ANALYZE 결과 정합성 검증
 * 대상). 단일 메서드 본문 50줄 미만이나 native SQL 직접 작성·파티션 프루닝 의도 단일 흐름 보존이 목적이다.</p>
 */
@RequiredArgsConstructor
public class RawDataCustomRepositoryImpl implements RawDataCustomRepository {

    @PersistenceContext
    private EntityManager em;

    private final JPAQueryFactory queryFactory;

    @Override
    @SuppressWarnings("unchecked")
    public List<RawDataLatestDto> findLatestByTagSrlNos(List<String> tagSrlNos) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — DISTINCT ON + 1시간 윈도우 파티션 프루닝 단일 흐름 보존
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT DISTINCT ON (tag_srl_no)
                               tag_srl_no, raw_val, corr_val, acq_dtm, quality_cd
                        FROM rawdata_1m_h
                        WHERE tag_srl_no IN (:tagSrlNos)
                          AND acq_dtm >= NOW() - INTERVAL '1 hour'
                        ORDER BY tag_srl_no, acq_dtm DESC
                        """)
                .setParameter("tagSrlNos", tagSrlNos)
                .getResultList();
        return rows.stream()
                .map(r -> new RawDataLatestDto(
                        (String) r[0],
                        (BigDecimal) r[1],
                        (BigDecimal) r[2],
                        JdbcTimestamps.toLocalDateTime(r[3]),
                        QualityCode.valueOf((String) r[4])
                ))
                .toList();
    }

    @Override
    public List<RawDataLatestDto> findByTagSrlNosAndDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 시점 범위 시계열 조회. acq_dtm 범위로 월 RANGE 파티션 프루닝 강제
        QRawData rawData = QRawData.rawData;
        return queryFactory
                .select(Projections.constructor(RawDataLatestDto.class,
                        rawData.tagSrlNo,
                        rawData.rawVal,
                        rawData.corrVal,
                        rawData.acqDtm,
                        rawData.qualityCd))
                .from(rawData)
                .where(rawData.tagSrlNo.in(tagSrlNos)
                        .and(rawData.acqDtm.goe(startDtm))
                        .and(rawData.acqDtm.lt(endDtm)))
                .orderBy(rawData.acqDtm.asc(), rawData.tagSrlNo.asc())
                .fetch();
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawDataBucketDto> findEnergyDeltaBuckets(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 적산전력량 버킷 차분 MAX-MIN GROUP BY 단일 흐름 보존.
        // GOOD only + raw_val 단독 (corr_val 미사용 — 적산값 HLV 차분 왜곡 방지, ot-integration.md §3).
        // acq_dtm 범위로 월 RANGE 파티션 프루닝 강제, :unit 은 InqUnit 4값 제약 text 바인드 (인젝션 안전).
        // GROUP BY 는 SELECT alias(base_dtm) 사용 — date_trunc(:unit) 를 SELECT·GROUP BY 양쪽에 두면
        // Hibernate 가 동일 named param 을 위치별 별도 ? 로 전개해 PostgreSQL 이 두 표현식을 다르게 판정(42803).
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT tag_srl_no,
                               date_trunc(:unit, acq_dtm) AS base_dtm,
                               MAX(raw_val) - MIN(raw_val) AS aggr_val
                        FROM rawdata_1m_h
                        WHERE tag_srl_no IN (:tagSrlNos)
                          AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
                          AND quality_cd = 'GOOD' AND raw_val IS NOT NULL
                        GROUP BY tag_srl_no, base_dtm
                        ORDER BY tag_srl_no, base_dtm
                        """)
                .setParameter("unit", dateTruncUnit)
                .setParameter("tagSrlNos", tagSrlNos)
                .setParameter("startDtm", startDtm)
                .setParameter("endDtm", endDtm)
                .getResultList();
        return mapToBuckets(rows);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawDataBucketSumDto> findEnergyDeltaBucketsTotal(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 태그별 버킷 차분 MAX-MIN 후 버킷 단위 전역 SUM 중첩 집계 단일 흐름 보존.
        // SUM(MAX-MIN) != MAX(SUM)-MIN(SUM): inner 에서 태그별 선차분 후 outer 에서 버킷 합산해야 의미를 가진다.
        // GOOD only + raw_val 단독 (corr_val 미사용 — 적산값 HLV 차분 왜곡 방지, ot-integration.md §3).
        // MAX-MIN 은 그룹 내 항상 ≥0이므로 음수 가드 불요 (delta=0 포함, GOOD 부재 버킷은 inner 결과 부재로 자연 sparse).
        // acq_dtm 범위로 월 RANGE 파티션 프루닝 강제, :unit 은 InqUnit 4값 제약 text 바인드 (인젝션 안전).
        // date_trunc(:unit) 는 inner SELECT 1회만 등장하고 inner GROUP BY 는 alias(base_dtm), outer 는 서브쿼리
        // 컬럼 base_dtm 참조 — :unit named param 이 SQL 전체에 1회만 전개되어 Hibernate 위치 파라미터 중복 전개(42803) 없음.
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT base_dtm, SUM(tag_delta) AS total_val
                        FROM (
                            SELECT tag_srl_no,
                                   date_trunc(:unit, acq_dtm) AS base_dtm,
                                   MAX(raw_val) - MIN(raw_val) AS tag_delta
                            FROM rawdata_1m_h
                            WHERE tag_srl_no IN (:tagSrlNos)
                              AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
                              AND quality_cd = 'GOOD' AND raw_val IS NOT NULL
                            GROUP BY tag_srl_no, base_dtm
                        ) t
                        GROUP BY base_dtm
                        ORDER BY base_dtm
                        """)
                .setParameter("unit", dateTruncUnit)
                .setParameter("tagSrlNos", tagSrlNos)
                .setParameter("startDtm", startDtm)
                .setParameter("endDtm", endDtm)
                .getResultList();
        return mapToBucketSums(rows);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawDataBucketDto> findAvgValueBuckets(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 주파수 버킷 평균 AVG(COALESCE) GROUP BY 단일 흐름 보존.
        // GOOD only + corr_val 우선 NULL 시 raw_val (FQI HLV 허용, ot-integration.md §3 VOI 선례 동형).
        // GROUP BY 는 SELECT alias(base_dtm) 사용 — date_trunc(:unit) 를 SELECT·GROUP BY 양쪽에 두면
        // Hibernate 가 동일 named param 을 위치별 별도 ? 로 전개해 PostgreSQL 이 두 표현식을 다르게 판정(42803).
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT tag_srl_no,
                               date_trunc(:unit, acq_dtm) AS base_dtm,
                               AVG(COALESCE(corr_val, raw_val)) AS aggr_val
                        FROM rawdata_1m_h
                        WHERE tag_srl_no IN (:tagSrlNos)
                          AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
                          AND quality_cd = 'GOOD'
                        GROUP BY tag_srl_no, base_dtm
                        ORDER BY tag_srl_no, base_dtm
                        """)
                .setParameter("unit", dateTruncUnit)
                .setParameter("tagSrlNos", tagSrlNos)
                .setParameter("startDtm", startDtm)
                .setParameter("endDtm", endDtm)
                .getResultList();
        return mapToBuckets(rows);
    }

    @Override
    public List<RawDataOnStateDto> findOnStateByTagSrlNosAndDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 가동(ON) 상태 시점 조회. 방안B: quality_cd='GOOD' AND raw_val=1 ON 필터를
        // 쿼리 레벨에서 적용하여 OFF/BAD/결측을 제외 → 시각열 1분 간극이 Service 런렝스 인코딩의 자연 세그먼트 경계.
        // acq_dtm 범위로 월 RANGE 파티션 프루닝 강제 + tag_srl_no ASC, acq_dtm ASC 정렬로 펌프별 단조증가 전제 보장.
        QRawData rawData = QRawData.rawData;
        return queryFactory
                .select(Projections.constructor(RawDataOnStateDto.class,
                        rawData.tagSrlNo,
                        rawData.acqDtm))
                .from(rawData)
                .where(rawData.tagSrlNo.in(tagSrlNos)
                        .and(rawData.acqDtm.goe(startDtm))
                        .and(rawData.acqDtm.lt(endDtm))
                        .and(rawData.qualityCd.eq(QualityCode.GOOD))
                        .and(rawData.rawVal.eq(BigDecimal.ONE)))
                .orderBy(rawData.tagSrlNo.asc(), rawData.acqDtm.asc())
                .fetch();
    }

    @Override
    public BigDecimal findMaxMinuteSumElpwr(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return null;
        }
        // §2.5 면책 (query-tuning.md §2) — 분(分)별 SUM 의 MAX(요금적용전력피크) 중첩 집계 단일 흐름 보존.
        // GOOD only + COALESCE(corr_val, raw_val) = effectiveVal SQL 동치 (findAvgValueBuckets 선례).
        // FROM 서브쿼리 중첩 집계(분별 SUM → MAX) 는 JPQL/Querydsl 표현 불가 → native 강제.
        // acq_dtm 범위로 월 RANGE 파티션 프루닝 강제 (12개월 구간). 빈 구간은 MAX 가 단일 NULL 행 반환.
        Object result = em.createNativeQuery("""
                        SELECT MAX(minute_sum) FROM (
                            SELECT acq_dtm, SUM(COALESCE(corr_val, raw_val)) AS minute_sum
                            FROM rawdata_1m_h
                            WHERE tag_srl_no IN (:tagSrlNos)
                              AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
                              AND quality_cd = 'GOOD'
                            GROUP BY acq_dtm
                        ) m
                        """)
                .setParameter("tagSrlNos", tagSrlNos)
                .setParameter("startDtm", startDtm)
                .setParameter("endDtm", endDtm)
                .getSingleResult();
        return (BigDecimal) result;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<RawDataBucketPeakDto> findMonthlyMaxMinuteSumElpwr(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 분(分)별 SUM 의 월별 MAX(최대 피크) 중첩 집계 단일 흐름 보존.
        // SUM(MAX) != MAX(SUM): inner 에서 같은 acq_dtm 의 전체 PWI 합산(분별 시설합) 후 outer 에서 월별 MAX 라야 의미.
        // GOOD only + COALESCE(corr_val, raw_val) = effectiveVal SQL 동치 (findMaxMinuteSumElpwr 선례).
        // FROM 서브쿼리 중첩 집계(분별 SUM → 월별 MAX) 는 JPQL/Querydsl 표현 불가 → native 강제.
        // outer GROUP BY 는 date_trunc('month', acq_dtm) 전체 표현식 — 'month' 는 리터럴(named param 아님)이라
        //   Hibernate 위치 파라미터 중복 전개(42803) 무관. acq_dtm 범위로 월 RANGE 파티션 프루닝 강제.
        // 결과는 sparse(데이터 있는 월만) — 6개 월 슬롯 채움(결측 월 null)은 호출 Service 책임.
        List<Object[]> rows = em.createNativeQuery("""
                        SELECT date_trunc('month', acq_dtm) AS base_dtm, MAX(minute_sum) AS peak_val
                        FROM (
                            SELECT acq_dtm, SUM(COALESCE(corr_val, raw_val)) AS minute_sum
                            FROM rawdata_1m_h
                            WHERE tag_srl_no IN (:tagSrlNos)
                              AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
                              AND quality_cd = 'GOOD'
                            GROUP BY acq_dtm
                        ) m
                        GROUP BY date_trunc('month', acq_dtm)
                        ORDER BY base_dtm
                        """)
                .setParameter("tagSrlNos", tagSrlNos)
                .setParameter("startDtm", startDtm)
                .setParameter("endDtm", endDtm)
                .getResultList();
        return mapToBucketPeaks(rows);
    }

    @Override
    public List<RawDataFacilitySumDto> findFacilityLatestMinuteSumElpwr(
            List<String> tags, List<String> roots, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 시설별 분(分) 시설합 PWI 의 마지막값 DISTINCT ON 단일 흐름 보존.
        // unnest(?, ?) 인라인 매핑 = 상수 VALUES read-only JOIN (시계열→마스터 FK 금지 정책 무관).
        // SUM(MAX) != MAX(SUM): 같은 acq_dtm 에서 SUM 후 시설별 마지막 분 선택 (분별 시설합 보존).
        // GOOD only + COALESCE(corr_val, raw_val) = effectiveVal. acq_dtm 범위로 월 RANGE 파티션 프루닝 강제.
        // Hibernate named-array 바인드 불확실성 회피 — Session.doReturningWork + JDBC setArray(text[]) 결정론적 바인드
        // (PLAN1 §구현 방향 3 — String[]+::text[] 폴백을 primary 로 채택).
        String sql = """
                WITH tag_facility(tag_srl_no, facility_id) AS (
                    SELECT * FROM unnest(?, ?)
                ),
                minute_sum AS (
                    SELECT tf.facility_id, r.acq_dtm,
                           SUM(COALESCE(r.corr_val, r.raw_val)) AS minute_sum
                    FROM rawdata_1m_h r
                    JOIN tag_facility tf ON tf.tag_srl_no = r.tag_srl_no
                    WHERE r.acq_dtm >= ? AND r.acq_dtm < ? AND r.quality_cd = 'GOOD'
                    GROUP BY tf.facility_id, r.acq_dtm
                )
                SELECT DISTINCT ON (facility_id)
                       facility_id, acq_dtm AS dtm, minute_sum AS val
                FROM minute_sum
                ORDER BY facility_id, acq_dtm DESC
                """;
        return em.unwrap(Session.class).doReturningWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setArray(1, connection.createArrayOf("text", tags.toArray()));
                ps.setArray(2, connection.createArrayOf("text", roots.toArray()));
                ps.setObject(3, startDtm);
                ps.setObject(4, endDtm);
                try (ResultSet rs = ps.executeQuery()) {
                    return drainFacilitySum(rs);
                }
            }
        });
    }

    @Override
    public List<RawDataFacilitySumDto> findFacilityBucketPeakElpwr(
            List<String> tags, List<String> roots,
            LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 시설별 버킷 시설합 PWI MAX + 발생 시각 argmax 중첩 집계 단일 흐름 보존.
        // unnest(?, ?) 인라인 매핑 = 상수 VALUES read-only JOIN. SUM(MAX) != MAX(SUM): acq_dtm 에서 SUM 후 버킷 MAX.
        // DISTINCT ON(facility_id) ORDER BY bucket_max DESC, base_dtm ASC = 동률 최이른 버킷 argmax (결정론적).
        // date_trunc(?, ...) 은 SELECT 1회 + GROUP BY alias(base_dtm) — 동일 표현식 중복 시 PostgreSQL 42803 회피
        //   (findEnergyDeltaBuckets 선례). acq_dtm 범위로 월 RANGE 파티션 프루닝 강제. GOOD only + effectiveVal.
        // Hibernate named-array 바인드 불확실성 회피 — Session.doReturningWork + JDBC setArray(text[]) 결정론적 바인드.
        String sql = """
                WITH tag_facility(tag_srl_no, facility_id) AS (
                    SELECT * FROM unnest(?, ?)
                ),
                minute_sum AS (
                    SELECT tf.facility_id, r.acq_dtm,
                           SUM(COALESCE(r.corr_val, r.raw_val)) AS minute_sum
                    FROM rawdata_1m_h r
                    JOIN tag_facility tf ON tf.tag_srl_no = r.tag_srl_no
                    WHERE r.acq_dtm >= ? AND r.acq_dtm < ? AND r.quality_cd = 'GOOD'
                    GROUP BY tf.facility_id, r.acq_dtm
                ),
                bucket_max AS (
                    SELECT facility_id, date_trunc(?, acq_dtm) AS base_dtm,
                           MAX(minute_sum) AS bucket_max
                    FROM minute_sum
                    GROUP BY facility_id, base_dtm
                )
                SELECT DISTINCT ON (facility_id)
                       facility_id, base_dtm AS dtm, bucket_max AS val
                FROM bucket_max
                ORDER BY facility_id, bucket_max DESC, base_dtm ASC
                """;
        return em.unwrap(Session.class).doReturningWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setArray(1, connection.createArrayOf("text", tags.toArray()));
                ps.setArray(2, connection.createArrayOf("text", roots.toArray()));
                ps.setObject(3, startDtm);
                ps.setObject(4, endDtm);
                ps.setString(5, dateTruncUnit);
                try (ResultSet rs = ps.executeQuery()) {
                    return drainFacilitySum(rs);
                }
            }
        });
    }

    @Override
    public List<RawDataInstrumentSumDto> findInstrumentMinuteSumElpwr(
            List<String> tags, List<String> instruments, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tags == null || tags.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 계측기별 분(分) 설비합 PWI 의 전체 시계열 단일 흐름 보존.
        // unnest(?, ?) 인라인 매핑 = 상수 VALUES read-only JOIN (시계열→마스터 FK 금지 정책 무관).
        // SUM(MAX) != MAX(SUM): 같은 acq_dtm 에서 설비별 SUM (다태그 설비 순시전력은 동시각 합이라야 의미).
        // 마지막 분만 추리는 findFacilityLatestMinuteSumElpwr 와 달리 DISTINCT ON 없이 전체 분 시계열 반환 (트렌드 차트).
        // GOOD only + COALESCE(corr_val, raw_val) = effectiveVal. acq_dtm 범위로 월 RANGE 파티션 프루닝 강제.
        // Hibernate named-array 바인드 불확실성 회피 — Session.doReturningWork + JDBC setArray(text[]) 결정론적 바인드.
        String sql = """
                WITH tag_instrument(tag_srl_no, instrument_id) AS (
                    SELECT * FROM unnest(?, ?)
                )
                SELECT ti.instrument_id, r.acq_dtm AS dtm,
                       SUM(COALESCE(r.corr_val, r.raw_val)) AS val
                FROM rawdata_1m_h r
                JOIN tag_instrument ti ON ti.tag_srl_no = r.tag_srl_no
                WHERE r.acq_dtm >= ? AND r.acq_dtm < ? AND r.quality_cd = 'GOOD'
                GROUP BY ti.instrument_id, r.acq_dtm
                ORDER BY ti.instrument_id, r.acq_dtm
                """;
        return em.unwrap(Session.class).doReturningWork(connection -> {
            try (PreparedStatement ps = connection.prepareStatement(sql)) {
                ps.setArray(1, connection.createArrayOf("text", tags.toArray()));
                ps.setArray(2, connection.createArrayOf("text", instruments.toArray()));
                ps.setObject(3, startDtm);
                ps.setObject(4, endDtm);
                try (ResultSet rs = ps.executeQuery()) {
                    return drainInstrumentSum(rs);
                }
            }
        });
    }

    /** native 집계 결과 행 ({@code tag_srl_no, base_dtm, aggr_val}) 을 {@link RawDataBucketDto} 로 매핑한다. */
    private List<RawDataBucketDto> mapToBuckets(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new RawDataBucketDto(
                        (String) r[0],
                        JdbcTimestamps.toLocalDateTime(r[1]),
                        (BigDecimal) r[2]
                ))
                .toList();
    }

    /** native 전역 합산 결과 행 ({@code base_dtm, total_val}) 을 {@link RawDataBucketSumDto} 로 매핑한다. */
    private List<RawDataBucketSumDto> mapToBucketSums(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new RawDataBucketSumDto(
                        JdbcTimestamps.toLocalDateTime(r[0]),
                        (BigDecimal) r[1]
                ))
                .toList();
    }

    /** native 월별 최대 피크 결과 행 ({@code base_dtm, peak_val}) 을 {@link RawDataBucketPeakDto} 로 매핑한다. */
    private List<RawDataBucketPeakDto> mapToBucketPeaks(List<Object[]> rows) {
        return rows.stream()
                .map(r -> new RawDataBucketPeakDto(
                        JdbcTimestamps.toLocalDateTime(r[0]),
                        (BigDecimal) r[1]
                ))
                .toList();
    }

    /**
     * 시설별 집계 ResultSet ({@code facility_id, dtm, val}) 을 {@link RawDataFacilitySumDto} 로 매핑한다.
     * {@code dtm} 슬롯은 PostgreSQL {@code timestamp} → {@link LocalDateTime} 직접 매핑한다.
     */
    private List<RawDataFacilitySumDto> drainFacilitySum(ResultSet rs) throws SQLException {
        List<RawDataFacilitySumDto> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new RawDataFacilitySumDto(
                    rs.getString("facility_id"),
                    rs.getObject("dtm", LocalDateTime.class),
                    rs.getBigDecimal("val")));
        }
        return result;
    }

    /**
     * 계측기별 집계 ResultSet ({@code instrument_id, dtm, val}) 을 {@link RawDataInstrumentSumDto} 로 매핑한다.
     * {@code dtm} 슬롯은 PostgreSQL {@code timestamp} → {@link LocalDateTime} 직접 매핑한다.
     */
    private List<RawDataInstrumentSumDto> drainInstrumentSum(ResultSet rs) throws SQLException {
        List<RawDataInstrumentSumDto> result = new ArrayList<>();
        while (rs.next()) {
            result.add(new RawDataInstrumentSumDto(
                    rs.getString("instrument_id"),
                    rs.getObject("dtm", LocalDateTime.class),
                    rs.getBigDecimal("val")));
        }
        return result;
    }
}
