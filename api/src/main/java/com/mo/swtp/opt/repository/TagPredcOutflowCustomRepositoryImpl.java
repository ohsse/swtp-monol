package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.QTagPrediction;
import com.mo.swtp.opt.dto.TagPredcOutflowDto;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;

/**
 * {@link TagPredcOutflowCustomRepository} 구현 — Querydsl 범위 조회 (운전현황분석 7번 섹션).
 *
 * <p>10번 섹션 {@code TagPredcRangeCustomRepositoryImpl.findByTagSrlNosAndPredcDtmRange} 와 동형 패턴
 * (Querydsl 범위 조회 + IN + predc_dtm 범위 + ORDER BY) 이나, 7번 섹션은 5·10번 Repository 자산을 재사용하지
 * 않고 별도 구현 클래스로 분리한다 (사용자 결정 2026-06-01 "사이클 독립성 우선" · 사용자 메모리 "사이클 간 자산
 * 자동 원용 금지" 정합). 결과 record 는 {@link TagPredcOutflowDto} 단일.</p>
 *
 * <p>인덱스 정합성: {@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)} 활용 → Index Scan +
 * 명시 Sort 노드 부재. {@code predc_dtm} 범위 조건으로 월 RANGE 파티션 프루닝 강제.</p>
 *
 * <p>{@code §2.5} 면책 영역 (DB 쿼리 빌더·튜닝 코드) — 인용 근거:
 * {@code .claude/rules/db/query-tuning.md §2 p6spy 슬로우 쿼리 활용} (EXPLAIN ANALYZE 결과 정합성 검증
 * 대상). 단일 메서드 본문 50줄 미만이나 Querydsl 직접 빌더·파티션 프루닝 의도 단일 흐름 보존이 목적이다.</p>
 */
@RequiredArgsConstructor
public class TagPredcOutflowCustomRepositoryImpl implements TagPredcOutflowCustomRepository {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<TagPredcOutflowDto> findByTagSrlNosAndPredcDtmRange(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm) {
        if (tagSrlNos == null || tagSrlNos.isEmpty()) {
            return List.of();
        }
        // §2.5 면책 (query-tuning.md §2) — 시점 범위 시계열 조회. predc_dtm 범위로 월 RANGE 파티션 프루닝 강제
        QTagPrediction tagPrediction = QTagPrediction.tagPrediction;
        return queryFactory
                .select(Projections.constructor(TagPredcOutflowDto.class,
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
