package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.TagPrediction;
import com.mo.swtp.opt.domain.TagPredictionId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 예측 시계열 태그 시점 범위 JPA 리포지토리.
 *
 * <p>{@link TagPrediction} 의 단순 CRUD 를 담당한다. 복합 PK 는 {@link TagPredictionId}
 * ({@code predc_id, predc_dtm}).</p>
 *
 * <p>운전현황분석-10번섹션 PLAN1 (2026-05-27) — 본 Repository 는 조회 전용 (테스트 fixture INSERT 외).
 * 시점 범위 시계열 단일 흐름은 {@link TagPredcRangeCustomRepository} 에서 제공한다.</p>
 *
 * <p>9번 섹션 {@link TagPredcLatestRepository} (1시간 윈도우 DISTINCT ON 단일 시점) 와 동일 엔티티
 * ({@link TagPrediction}) 를 가리키나, 본 사이클은 시점 범위 시계열 BETWEEN 조회 (Querydsl) 만 책임지며
 * 9번 섹션의 단일 시점 책임을 침범하지 않는다 (PLAN1 §Repository 신규 — 의도 분리).</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 의 논리 참조. 마스터 결합 조회는 Service 계층에서 별도로 조합한다.</p>
 */
public interface TagPredcRangeRepository
        extends JpaRepository<TagPrediction, TagPredictionId>, TagPredcRangeCustomRepository {
}
