package com.mo.swtp.raw.repository;

import com.mo.swtp.raw.dto.RawDataBucketDto;
import com.mo.swtp.raw.dto.RawDataBucketPeakDto;
import com.mo.swtp.raw.dto.RawDataBucketSumDto;
import com.mo.swtp.raw.dto.RawDataFacilitySumDto;
import com.mo.swtp.raw.dto.RawDataInstrumentSumDto;
import com.mo.swtp.raw.dto.RawDataLatestDto;
import com.mo.swtp.raw.dto.RawDataOnStateDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * SCADA 원시 데이터 커스텀 조회 인터페이스.
 *
 * <p>송수펌프제어분석-3번섹션 PLAN1 (2026-05-13) — 2번 섹션 자산
 * ({@code findLatestByTagSrlNos(List<String>, LocalDateTime)} Querydsl 서브쿼리 구현) 일괄 폐기 후
 * native DISTINCT ON 단일 메서드로 재정의 (사용자 결정 2026-05-13 "DwtStatus 부분도 전부 폐기 다음 섹션
 * 진행할 때 다시설계").</p>
 *
 * <p>운전현황분석-5번섹션 PLAN1 (2026-05-21) — 시계열 시점 범위 조회 메서드 추가
 * ({@link #findByTagSrlNosAndDtmRange}). Querydsl 구현으로 시점 범위 시계열 응답 지원.</p>
 *
 * <p>시계열 → 마스터 FK 금지 정책 ({@code .claude/rules/db/partitioning-and-retention.md §1}) 정합.
 * 마스터 결합은 호출 Service 가 별도로 조합한다.</p>
 */
public interface RawDataCustomRepository {

    /**
     * 태그 시리얼번호 목록의 각 태그 최신 측정값을 단일 native SQL 로 조회한다.
     *
     * <p>구현은 PostgreSQL DISTINCT ON 절 + {@code idx_rawdata_1m_h_tag_time
     * (tag_srl_no, acq_dtm DESC)} 인덱스 활용을 강제한다. {@code acq_dtm >= NOW() - INTERVAL '1 hour'}
     * 하한으로 파티션 프루닝을 강제 (송수펌프제어분석-3번섹션 ANALYZE1 안건 5·6 — 1시간 윈도우 정책).</p>
     *
     * <p>측정 이력이 1시간 윈도우 내 없는 태그는 응답 목록에 포함되지 않는다.</p>
     *
     * @param tagSrlNos 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @return 태그별 최신 측정값 List (각 element 는 {@link RawDataLatestDto})
     */
    List<RawDataLatestDto> findLatestByTagSrlNos(List<String> tagSrlNos);

    /**
     * 태그 시리얼번호 목록의 지정 시점 범위 시계열 측정값을 Querydsl 로 조회한다.
     *
     * <p>운전현황분석-5번섹션 PLAN1 (2026-05-21) — 금일/비교 기간 1분 시계열 조회 전용 메서드.
     * 조건: {@code tag_srl_no IN :tagSrlNos AND acq_dtm >= :startDtm AND acq_dtm < :endDtm}.
     * 정렬: {@code acq_dtm ASC, tag_srl_no ASC} — Service 측 시점별 grouping 의 안정성 보장.</p>
     *
     * <p>인덱스 정합: {@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)} 활용 +
     * {@code acq_dtm} 범위 조건으로 월 RANGE 파티션 프루닝 강제 ({@code EXPLAIN ANALYZE} 200ms 이내 SLA).</p>
     *
     * @param tagSrlNos 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param startDtm  조회 시작 일시 (inclusive)
     * @param endDtm    조회 종료 일시 (exclusive)
     * @return 시점 범위 내 측정값 List (각 element 는 {@link RawDataLatestDto}, {@code acq_dtm} 오름차순)
     */
    List<RawDataLatestDto> findByTagSrlNosAndDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);

    /**
     * 적산전력량(PWQ) 태그의 시계열 버킷별 차분(전력량 kWh)을 단일 native SQL 로 조회한다.
     *
     * <p>버킷은 {@code date_trunc(:dateTruncUnit, acq_dtm)} 으로 시/일/월/년 단위 절삭하며, 각 버킷의
     * 전력량은 적산 미터값의 {@code MAX(raw_val) - MIN(raw_val)} 차분으로 산정한다. {@code quality_cd = 'GOOD'}
     * + {@code raw_val IS NOT NULL} 행만 집계하며 {@code corr_val} 은 사용하지 않는다 — 적산값 Hold Last Value
     * 차분 왜곡 방지 ({@code .claude/rules/ot-integration.md §3} PWQ 결측 정책, 송수펌프가동이력_3번섹션 ANALYZE1 안건 2·4).</p>
     *
     * <p>음수 차분(적산 카운터 리셋·롤오버)은 본 쿼리에서 걸러내지 않으며, 호출 Service 가 단조증가 가정 위반으로
     * 생략 처리한다 (PLAN1 §구현 방향 6 버킷 가드).</p>
     *
     * <p>{@code acq_dtm} 범위 조건으로 월 RANGE 파티션 프루닝 강제 + {@code idx_rawdata_1m_h_tag_time
     * (tag_srl_no, acq_dtm DESC)} 인덱스 활용. 결과 정렬: {@code tag_srl_no ASC, base_dtm ASC}.</p>
     *
     * @param tagSrlNos     태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param startDtm      조회 시작 일시 (inclusive)
     * @param endDtm        조회 종료 일시 (exclusive)
     * @param dateTruncUnit PostgreSQL {@code date_trunc} 첫 인자 ({@code InqUnit.getDateTruncUnit()} — hour/day/month/year)
     * @return 태그별·버킷별 전력량 차분 List (각 element 는 {@link RawDataBucketDto})
     */
    List<RawDataBucketDto> findEnergyDeltaBuckets(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit);

    /**
     * 적산전력량(PWQ) 태그 집합의 시계열 버킷별 전력량(kWh)을 버킷 단위로 전역 합산하여 단일 native SQL 로 조회한다.
     *
     * <p>{@link #findEnergyDeltaBuckets}(태그별·버킷별 차분 반환) 와 달리, 태그 차원을 SQL 단계에서 합산해
     * 소거한 단일 시계열을 반환한다 — 사용량트렌드 2번섹션은 사용자 지정 기간(최대 13개월)·시/일/월 가변 집계라
     * {@code HOUR×396일×N태그} 행을 호출 측으로 materialize 하면 대용량이므로, 버킷별 전역 합산까지 DB 에서 수행한다
     * (사용량트렌드-2번섹션 ANALYZE1 안건 2, DBA 비준).</p>
     *
     * <p>중첩 집계 구조 — inner 에서 태그별 버킷 차분({@code MAX(raw_val)-MIN(raw_val)}) 후 outer 에서 버킷 단위
     * {@code SUM} 한다 ({@code SUM(MAX-MIN) != MAX(SUM)-MIN(SUM)} — 태그별 선차분 후 합산 의무). {@code quality_cd
     * = 'GOOD'} + {@code raw_val IS NOT NULL} 행만 집계하며 {@code corr_val} 은 사용하지 않는다 — 적산값 Hold
     * Last Value 차분 왜곡 방지 ({@code .claude/rules/ot-integration.md §3} PWQ 결측 정책). {@code MAX-MIN} 은
     * 그룹 내 항상 ≥0이므로 음수 가드 불요 (delta=0 버킷은 포함, GOOD 데이터 없는 버킷은 자연 sparse).</p>
     *
     * <p>{@code acq_dtm} 범위 조건으로 월 RANGE 파티션 프루닝 강제 + {@code idx_rawdata_1m_h_tag_time
     * (tag_srl_no, acq_dtm DESC)} 인덱스 활용. 결과 정렬: {@code base_dtm ASC} (버킷 시작 일시 오름차순).</p>
     *
     * @param tagSrlNos     PWQ 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param startDtm      조회 시작 일시 (inclusive)
     * @param endDtm        조회 종료 일시 (exclusive — 종료일 익일 자정)
     * @param dateTruncUnit PostgreSQL {@code date_trunc} 첫 인자 ({@code InqUnit.getDateTruncUnit()} — hour/day/month)
     * @return 버킷별 전역 합산 전력량 List (각 element 는 {@link RawDataBucketSumDto}, {@code base_dtm} 오름차순)
     */
    List<RawDataBucketSumDto> findEnergyDeltaBucketsTotal(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit);

    /**
     * 주파수(FQI) 태그의 시계열 버킷별 평균값(Hz)을 단일 native SQL 로 조회한다.
     *
     * <p>버킷은 {@code date_trunc(:dateTruncUnit, acq_dtm)} 으로 시/일/월/년 단위 절삭하며, 각 버킷의
     * 주파수는 {@code AVG(COALESCE(corr_val, raw_val))} 로 산정한다. {@code quality_cd = 'GOOD'} 행만
     * 집계한다. FQI 는 Hold Last Value 허용 측정유형이므로 {@code corr_val} 우선 + NULL 시 {@code raw_val}
     * ({@code .claude/rules/ot-integration.md §3} VOI 선례 동형, 2번섹션 {@code effectiveVal} 정합).</p>
     *
     * <p>{@code acq_dtm} 범위 조건으로 월 RANGE 파티션 프루닝 강제. 결과 정렬: {@code tag_srl_no ASC, base_dtm ASC}.</p>
     *
     * @param tagSrlNos     태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param startDtm      조회 시작 일시 (inclusive)
     * @param endDtm        조회 종료 일시 (exclusive)
     * @param dateTruncUnit PostgreSQL {@code date_trunc} 첫 인자 (hour/day/month/year)
     * @return 태그별·버킷별 주파수 평균 List (각 element 는 {@link RawDataBucketDto})
     */
    List<RawDataBucketDto> findAvgValueBuckets(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit);

    /**
     * 가동상태(OPS) 태그의 지정 시점 범위 가동(ON) 시각을 Querydsl 로 조회한다 (방안B — 쿼리 레벨 ON 필터).
     *
     * <p>조건: {@code tag_srl_no IN :tagSrlNos AND acq_dtm >= :startDtm AND acq_dtm < :endDtm
     * AND quality_cd = 'GOOD' AND raw_val = 1}. ON 행만 투영하므로 OFF({@code raw_val = 0})·BAD·UNCERTAIN·결측
     * 시점은 결과에서 제외되며, 결과 시각열의 1분 간극(gap) 이 호출 Service 의 런렝스 인코딩에서 자연 세그먼트
     * 경계가 된다. OPS 는 Hold Last Value 미적용 — BAD 구간을 ON 으로 이어붙이지 않는다 (통신단절 펌프 ON 오인
     * 금지, {@code .claude/rules/ot-integration.md §3}, 송수펌프가동이력_4번섹션 ANALYZE1 안건 4·5).</p>
     *
     * <p>정렬: {@code tag_srl_no ASC, acq_dtm ASC} — Service 측 펌프(태그)별 런렝스 인코딩의 시각 단조증가 전제.
     * 인덱스 정합: {@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)} 활용 + {@code acq_dtm} 범위
     * 조건으로 월 RANGE 파티션 프루닝 강제 ({@code EXPLAIN ANALYZE} 200ms 이내 SLA).</p>
     *
     * @param tagSrlNos 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param startDtm  조회 시작 일시 (inclusive)
     * @param endDtm    조회 종료 일시 (exclusive)
     * @return 가동(ON) 상태 시각 List (각 element 는 {@link RawDataOnStateDto}, {@code tag_srl_no} 그룹 내 {@code acq_dtm} 오름차순)
     */
    List<RawDataOnStateDto> findOnStateByTagSrlNosAndDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);

    /**
     * 지정 구간 분(分)단위 순시전력 합산값의 최댓값(요금적용전력피크, kW)을 단일 native SQL 로 조회한다.
     *
     * <p>전력피크분석-2번섹션 PLAN1 (2026-06-05) — 분별로 GOOD 품질 PWI 태그 측정값을 합산
     * ({@code SUM(COALESCE(corr_val, raw_val))}) 한 뒤, 구간(보통 최근 12개월) 내 그 분단위 합산값의 MAX 를
     * 반환한다. 결측 정책: 그 시각에 존재하는 GOOD 값만 합산(부분합 허용) — 총순시전력과 동일 정책.
     * 발생 시각은 반환하지 않는다(값만 — 사용자 결정 3).</p>
     *
     * <p>{@code acq_dtm} 범위 조건({@code >= startDtm AND < endDtm}) 으로 월 RANGE 파티션 프루닝을 강제하며
     * {@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)} 인덱스를 활용한다. 분(分)별 SUM 후 그
     * 합산값의 MAX 는 FROM 서브쿼리 중첩 집계로 JPQL/Querydsl 표현이 불가하여 native 로 작성한다.</p>
     *
     * @param tagSrlNos PWI 태그 시리얼번호 목록 (빈 리스트 시 {@code null} 반환)
     * @param startDtm  조회 시작 일시 (inclusive, 보통 NOW()-12개월)
     * @param endDtm    조회 종료 일시 (exclusive, 보통 NOW())
     * @return 분단위 합산값의 MAX (해당 구간 GOOD 데이터 부재 시 {@code null} — Service 가 ZERO fallback)
     */
    BigDecimal findMaxMinuteSumElpwr(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);

    /**
     * 지정 구간 월별로 분(分)단위 순시전력 합산값의 최댓값(최대 피크, kW)을 단일 native SQL 로 조회한다.
     *
     * <p>사용량트렌드-3번섹션 PLAN1 (2026-06-11) — 같은 {@code acq_dtm} 의 GOOD 품질 PWI 태그 측정값을 합산
     * ({@code SUM(COALESCE(corr_val, raw_val))}) 한 분별 합산값을 월 버킷({@code date_trunc('month', acq_dtm)})
     * 단위로 MAX 하여 그 달의 최대 피크를 반환한다 ({@code MAX_over_month(SUM_over_facilities(PWI per minute))}).
     * {@link #findMaxMinuteSumElpwr}(전 구간 단일 MAX) 의 분별 합산 구조를 월 버킷 MAX + 다월 윈도우로 확장한 것이다.
     * 결측 정책: 그 시각에 존재하는 GOOD 값만 합산(부분합 허용) — 요금적용전력피크와 동일 정책.</p>
     *
     * <p>분(分)별 SUM 후 월별 MAX 의 중첩 집계 ({@code SUM(MAX) != MAX(SUM)} — 전체 설비 순시전력은 동시각 합이라야
     * 의미를 가진다) 는 FROM 서브쿼리 중첩 집계로 JPQL/Querydsl 표현이 불가하여 native 로 작성한다. outer
     * {@code GROUP BY date_trunc('month', acq_dtm)} 는 {@code 'month'} 리터럴(named param 아님)이라 Hibernate
     * 위치 파라미터 중복 전개(42803) 없음. {@code acq_dtm} 범위 조건({@code >= startDtm AND < endDtm}) 으로 월
     * RANGE 파티션 프루닝을 강제하며 {@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)} 인덱스를 활용한다.</p>
     *
     * <p>결과는 데이터가 존재하는 월만 포함(sparse, {@code base_dtm} 오름차순)하며, 6개 월 슬롯 채움(결측 월
     * {@code null})은 호출 Service 의 책임이다.</p>
     *
     * @param tagSrlNos PWI 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param startDtm  조회 시작 일시 (inclusive, 보통 당월-5개월 1일 00:00)
     * @param endDtm    조회 종료 일시 (exclusive, 보통 당월+1개월 1일 00:00)
     * @return 월별 최대 피크 List (각 element 는 {@link RawDataBucketPeakDto}, {@code base_dtm} 오름차순, sparse)
     */
    List<RawDataBucketPeakDto> findMonthlyMaxMinuteSumElpwr(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);

    /**
     * 운영시설별 조회기간 마지막 분(分)의 시설합 순시전력(kW)을 단일 native SQL 로 조회한다.
     *
     * <p>시설별사용량-2번섹션 PLAN1 — {@code tags[i]} 태그가 운영시설 {@code roots[i]} 로 매핑되는 두 평행 배열을
     * {@code unnest(tags, roots)} 인라인 매핑으로 받아, GOOD 품질 PWI 측정값을 같은 {@code acq_dtm} 에서 시설별로
     * 합산({@code SUM(COALESCE(corr_val, raw_val))}) 한 뒤, 각 시설의 가장 늦은 분의 시설합을
     * {@code DISTINCT ON (facility_id) ORDER BY acq_dtm DESC} 로 1건씩 반환한다. {@code unnest} 인라인 매핑은
     * 상수 VALUES read-only JOIN 이므로 시계열 → 마스터 FK 금지 정책({@code db/partitioning-and-retention.md §1})
     * 과 무관하다.</p>
     *
     * <p>분(分)별 시설합을 SQL 에서 같은 {@code acq_dtm} 으로 SUM 해야 한다 ({@code SUM(MAX) != MAX(SUM)} 함정 —
     * 다태그 시설의 순시전력은 동시각 합이라야 의미를 가진다). {@code acq_dtm} 범위 조건({@code >= startDtm AND
     * < endDtm}) 으로 월 RANGE 파티션 프루닝 강제 + {@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)}
     * 인덱스 활용.</p>
     *
     * @param tags     PWI 태그 시리얼번호 배열 (운영시설 매핑 source — {@code roots} 와 동일 길이·정렬, 빈 리스트 시 빈 List 반환)
     * @param roots    각 태그의 운영시설 루트 ID 배열 ({@code tags} 와 평행)
     * @param startDtm 조회 시작 일시 (inclusive)
     * @param endDtm   조회 종료 일시 (exclusive)
     * @return 운영시설별 마지막 분의 시설합 PWI ({@code dtm} = 마지막 분 {@code acq_dtm}, {@code value} = 시설합 kW)
     */
    List<RawDataFacilitySumDto> findFacilityLatestMinuteSumElpwr(
            List<String> tags, List<String> roots, LocalDateTime startDtm, LocalDateTime endDtm);

    /**
     * 운영시설별 집계단위 버킷의 시설합 순시전력(kW) MAX 와 발생 시각을 단일 native SQL 로 조회한다.
     *
     * <p>시설별사용량-2번섹션 PLAN1 — {@code unnest(tags, roots)} 인라인 매핑으로 GOOD 품질 PWI 를 같은
     * {@code acq_dtm} 에서 시설별로 합산한 뒤({@code minute_sum}), {@code date_trunc(:unit, acq_dtm)} 버킷별 시설합
     * MAX({@code bucket_max}) 를 구하고, 각 시설의 버킷 MAX 중 최댓값과 그 발생 버킷 시작 시각을
     * {@code DISTINCT ON (facility_id) ORDER BY bucket_max DESC, base_dtm ASC} 로 1건씩 반환한다 (동률 시 가장 이른
     * 버킷 — 결정론적 argmax). 최대전력 값 자체는 집계단위 불변(= 전체기간 최대 분합) 이며 발생 시각의 입도만
     * 집계단위에 따라 달라진다 (PLAN1 §최대전력 해석).</p>
     *
     * <p>분(分)별 시설합을 SQL 에서 같은 {@code acq_dtm} 으로 SUM 해야 한다 ({@code SUM(MAX) != MAX(SUM)} 함정).
     * {@code acq_dtm} 범위 조건으로 월 RANGE 파티션 프루닝 강제. {@code :unit} 은 {@link com.mo.swtp.common.enumtype.InqUnit}
     * 4값 제약 text 바인드 (인젝션 안전).</p>
     *
     * @param tags          PWI 태그 시리얼번호 배열 (운영시설 매핑 source — {@code roots} 와 동일 길이·정렬, 빈 리스트 시 빈 List 반환)
     * @param roots         각 태그의 운영시설 루트 ID 배열 ({@code tags} 와 평행)
     * @param startDtm      조회 시작 일시 (inclusive)
     * @param endDtm        조회 종료 일시 (exclusive)
     * @param dateTruncUnit PostgreSQL {@code date_trunc} 첫 인자 ({@code InqUnit.getDateTruncUnit()} — hour/day/month)
     * @return 운영시설별 최대전력 ({@code dtm} = 발생 버킷 시작 시각, {@code value} = 버킷 시설합 MAX, kW)
     */
    List<RawDataFacilitySumDto> findFacilityBucketPeakElpwr(
            List<String> tags, List<String> roots,
            LocalDateTime startDtm, LocalDateTime endDtm, String dateTruncUnit);

    /**
     * 계측기(설비)별 조회기간 전체 분(分)의 설비합 순시전력(kW) 시계열을 단일 native SQL 로 조회한다.
     *
     * <p>설비별사용량-7번섹션 PLAN1 — {@code tags[i]} 태그가 계측기 {@code instruments[i]} 로 매핑되는 두 평행
     * 배열을 {@code unnest(tags, instruments)} 인라인 매핑으로 받아, GOOD 품질 PWI 측정값을 같은 {@code acq_dtm}
     * 에서 계측기별로 합산({@code SUM(COALESCE(corr_val, raw_val))}) 한 결과를 분(分) 단위로 1행씩 모두 반환한다.
     * 마지막 분 1건만 {@code DISTINCT ON} 으로 추리는 {@link #findFacilityLatestMinuteSumElpwr} 와 달리,
     * 본 메서드는 트렌드 차트용이므로 조회기간 전체 분 시계열을 압축 없이 반환한다 ({@code DISTINCT ON} 없음).
     * {@code unnest} 인라인 매핑은 상수 VALUES read-only JOIN 이므로 시계열 → 마스터 FK 금지 정책
     * ({@code db/partitioning-and-retention.md §1}) 과 무관하다.</p>
     *
     * <p>분(分)별 설비합을 SQL 에서 같은 {@code acq_dtm} 으로 SUM 해야 한다 ({@code SUM(MAX) != MAX(SUM)} 함정 —
     * 다태그 설비의 순시전력은 동시각 합이라야 의미를 가진다). {@code quality_cd = 'GOOD'} 행만 합산하여
     * BAD/UNCERTAIN·결측은 제외한다 (부분합 허용 — {@code .claude/rules/ot-integration.md §3} PWI "집계 제외"
     * 정합, Hold Last Value 미적용). {@code acq_dtm} 범위 조건({@code >= startDtm AND < endDtm}) 으로 월 RANGE
     * 파티션 프루닝 강제 + {@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)} 인덱스 활용. 결과 정렬:
     * {@code instrument_id ASC, acq_dtm ASC}.</p>
     *
     * @param tags        PWI 태그 시리얼번호 배열 (계측기 매핑 source — {@code instruments} 와 동일 길이·정렬, 빈 리스트 시 빈 List 반환)
     * @param instruments 각 태그의 소속 계측기 ID 배열 ({@code tags} 와 평행)
     * @param startDtm    조회 시작 일시 (inclusive)
     * @param endDtm      조회 종료 일시 (exclusive)
     * @return 계측기별·분(分)별 설비합 PWI List (각 element 는 {@link RawDataInstrumentSumDto}, {@code instrument_id ASC, acq_dtm ASC})
     */
    List<RawDataInstrumentSumDto> findInstrumentMinuteSumElpwr(
            List<String> tags, List<String> instruments, LocalDateTime startDtm, LocalDateTime endDtm);
}
