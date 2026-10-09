package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.dto.TagPredcLatestDto;
import java.util.List;

/**
 * AI 예측 시계열 태그 최신값 커스텀 조회 인터페이스.
 *
 * <p>운전현황분석-9번섹션 PLAN1 (2026-05-21) — 활성 시설의 OPS/PWI/FRI 예측 태그별 1시간 윈도우 내 최신
 * 예측값 1건씩 일괄 조회. 4번 섹션 {@code RawDataCustomRepository.findLatestByTagSrlNos} 동형 정책
 * (DISTINCT ON + 1시간 윈도우 + 파티션 프루닝).</p>
 *
 * <p>섹션 7 {@link TagPredictionCustomRepository#findNearestByTagSrlNos} (현황 최신시각+1시간 근접 매칭
 * LATERAL) 와 의도·SQL 흐름이 다르다 — 본 사이클은 단순 최신값 추출 (DISTINCT ON) 이며 별도 Repository
 * 로 분리한다 ({@code coding-discipline.md §3} 정밀한 수정 — 7번 산출물 직접 수정 회피).</p>
 *
 * <p>시계열 → 마스터 FK 금지 정책 ({@code .claude/rules/db/partitioning-and-retention.md §1}) 정합.
 * 마스터 결합은 호출 Service 가 별도로 조합한다.</p>
 */
public interface TagPredcLatestCustomRepository {

    /**
     * 태그 시리얼번호 목록의 각 태그별 1시간 윈도우 내 최신 예측값 1건씩을 단일 native SQL 로 조회한다.
     *
     * <p>구현은 PostgreSQL DISTINCT ON 절 + {@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)}
     * 인덱스 활용을 강제한다. {@code predc_dtm >= NOW() - INTERVAL '1 hour'} 하한으로 월 RANGE 파티션
     * 프루닝 강제 (4번 섹션 {@code RawDataCustomRepository.findLatestByTagSrlNos} 동형 정책).</p>
     *
     * <p>1시간 윈도우 내 예측 행이 없는 태그는 응답 목록에 포함되지 않는다 (Service 가 결측으로 매핑).</p>
     *
     * @param tagSrlNos 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @return 태그별 최신 예측값 List (각 element 는 {@link TagPredcLatestDto})
     */
    List<TagPredcLatestDto> findLatestByTagSrlNos(List<String> tagSrlNos);
}
