package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.QTagPrediction;
import com.mo.swtp.opt.dto.TagPredcRangeDto;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * {@link TagPredcRangeCustomRepository} 구현 — Querydsl 범위 조회.
 *
 * <p>운전현황분석-10번섹션 PLAN1 (2026-05-27) — 5번 섹션 {@code RawDataCustomRepositoryImpl.findByTagSrlNosAndDtmRange}
 * 동형 패턴 (Querydsl 범위 조회 + IN+BETWEEN+ORDER BY). 데이터 소스만 {@code rawdata_1m_h} →
 * {@code predc_1m_h}, 파티션 키 {@code acq_dtm} → {@code predc_dtm}, 컬럼은 {@code raw_val}·{@code corr_val}·
 * {@code quality_cd} 3개 제거 후 {@code predc_val} 단일.</p>
 *
 * <p>인덱스 정합성: {@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)} 활용 → Index Scan +
 * 명시 Sort 노드 부재. {@code predc_dtm} 범위 조건으로 월 RANGE 파티션 프루닝 강제 (현재월~익월 2개 파티션 스캔).</p>
 *
 * <p>{@code §2.5} 면책 영역 (DB 쿼리 빌더·튜닝 코드) — 인용 근거:
 * {@code .claude/rules/db/query-tuning.md §2 p6spy 슬로우 쿼리 활용} (EXPLAIN ANALYZE 결과 정합성 검증
 * 대상). 단일 메서드 본문 50줄 미만이나 Querydsl 직접 빌더·파티션 프루닝 의도 단일 흐름 보존이 목적이다.</p>
 */
@RequiredArgsConstructor
public class TagPredcRangeCustomRepositoryImpl implements TagPredcRangeCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<TagPredcRangeDto> findByTagSrlNosAndPredcDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 시점 범위 시계열 조회. predc_dtm 범위로 월 RANGE 파티션 프루닝 강제
        QTagPrediction tagPrediction = QTagPrediction.tagPrediction;
        return queryFactory
                .select(Projections.constructor(TagPredcRangeDto.class,
                        tagPrediction.tagSrlNo,
                        tagPrediction.predcDtm,
                        tagPrediction.predcVal))
                .from(tagPrediction)
                .where(tagPrediction.tagSrlNo.in(tagSrlNos)
                        .and(tagPrediction.predcDtm.goe(startDtm))
                        .and(tagPrediction.predcDtm.lt(endDtm)))
                .orderBy(tagPrediction.predcDtm.asc(), tagPrediction.tagSrlNo.asc())
                .fetch();
    }
}
