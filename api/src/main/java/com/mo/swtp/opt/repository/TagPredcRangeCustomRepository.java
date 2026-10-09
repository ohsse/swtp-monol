package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.dto.TagPredcRangeDto;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 예측 시계열 태그 시점 범위 커스텀 조회 인터페이스.
 *
 * <p>운전현황분석-10번섹션 PLAN1 (2026-05-27) — 활성 시설의 OPS/PWI/FRI 예측 태그별 금일 자정~익일 자정전
 * 범위 시계열 예측값 일괄 조회. 5번 섹션 {@link com.mo.swtp.raw.repository.RawDataCustomRepository#findByTagSrlNosAndDtmRange}
 * 동형 정책 (Querydsl 범위 조회 + acq_dtm/predc_dtm 범위 조건으로 월 RANGE 파티션 프루닝 강제).</p>
 *
 * <p>9번 섹션 {@link TagPredcLatestCustomRepository#findLatestByTagSrlNos} (1시간 윈도우 DISTINCT ON 단일
 * 시점) 와 의도·SQL 흐름이 다르다 — 본 사이클은 시점 범위 시계열 BETWEEN 조회이며 별도 Repository 로
 * 분리한다 (사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합 + {@code coding-discipline.md §3} 정밀한 수정).</p>
 *
 * <p>시계열 → 마스터 FK 금지 정책 ({@code .claude/rules/db/partitioning-and-retention.md §1}) 정합.
 * 마스터 결합은 호출 Service 가 별도로 조합한다.</p>
 */
public interface TagPredcRangeCustomRepository {

    /**
     * 태그 시리얼번호 목록의 지정 시점 범위 시계열 예측값을 Querydsl 로 조회한다.
     *
     * <p>운전현황분석-10번섹션 PLAN1 (2026-05-27) — 금일 자정~익일 자정 시점 범위 1분 시계열 예측값 조회 전용.
     * 조건: {@code tag_srl_no IN :tagSrlNos AND predc_dtm >= :startDtm AND predc_dtm < :endDtm}.
     * 정렬: {@code predc_dtm ASC, tag_srl_no ASC} — Service 측 시점별 grouping 안정성 보장.</p>
     *
     * <p>인덱스 정합: {@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)} 활용 + {@code predc_dtm}
     * 범위 조건으로 월 RANGE 파티션 프루닝 강제 ({@code EXPLAIN ANALYZE} 200ms 이내 SLA).</p>
     *
     * @param tagSrlNos 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param startDtm  조회 시작 일시 (inclusive)
     * @param endDtm    조회 종료 일시 (exclusive)
     * @return 시점 범위 내 예측값 List (각 element 는 {@link TagPredcRangeDto}, {@code predc_dtm} 오름차순)
     */
    List<TagPredcRangeDto> findByTagSrlNosAndPredcDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);
}
