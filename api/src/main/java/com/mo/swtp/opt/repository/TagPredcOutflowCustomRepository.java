package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.dto.TagPredcOutflowDto;
import java.time.LocalDateTime;
import java.util.List;

/**
 * AI 예측 시계열 유출 커스텀 조회 인터페이스 — 운전현황분석 7번 섹션.
 *
 * <p>활성 시설의 유출 유량(FRI)·압력(PRI)·펌프 가동상태(OPS) 예측값을 금일 00:00 ~ 현재시간 1분 시계열로
 * 표출하기 위한 시점 범위 조회 단일 메서드를 정의한다.</p>
 *
 * <p>10번 섹션 {@link TagPredcRangeCustomRepository#findByTagSrlNosAndPredcDtmRange} 와 시그니처가 동일하나,
 * 7번 섹션은 5·10번 Repository 자산을 재사용하지 않고 별도 인터페이스 + 별도 결과 record({@link TagPredcOutflowDto})
 * 로 분리한다 (사용자 결정 2026-06-01 "사이클 독립성 우선" · 사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합).</p>
 *
 * <p>시계열 → 마스터 FK 금지 정책 ({@code .claude/rules/db/partitioning-and-retention.md §1}) 정합.
 * 마스터 결합은 호출 Service 가 별도로 조합한다.</p>
 */
public interface TagPredcOutflowCustomRepository {

    /**
     * 태그 시리얼번호 목록의 지정 시점 범위 예측 시계열을 Querydsl 로 조회한다.
     *
     * <p>조건: {@code tag_srl_no IN :tagSrlNos AND predc_dtm >= :startDtm AND predc_dtm < :endDtm}.
     * 정렬: {@code predc_dtm ASC, tag_srl_no ASC} — Service 측 시점별 grouping 의 안정성 보장.</p>
     *
     * <p>인덱스 정합: {@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)} 활용 + {@code predc_dtm} 범위
     * 조건으로 월 RANGE 파티션 프루닝 강제 ({@code EXPLAIN ANALYZE} 200ms 이내 SLA).</p>
     *
     * @param tagSrlNos 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param startDtm  조회 시작 일시 (inclusive)
     * @param endDtm    조회 종료 일시 (exclusive)
     * @return 시점 범위 내 예측값 List (각 element 는 {@link TagPredcOutflowDto}, {@code predc_dtm} 오름차순)
     */
    List<TagPredcOutflowDto> findByTagSrlNosAndPredcDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);
}
