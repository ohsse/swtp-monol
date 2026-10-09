package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.TagPrediction;
import com.mo.swtp.opt.domain.TagPredictionId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 예측 시계열 태그 최신값 JPA 리포지토리.
 *
 * <p>{@link TagPrediction} 의 단순 CRUD 를 담당한다. 복합 PK 는 {@link TagPredictionId}
 * ({@code predc_id, predc_dtm}).</p>
 *
 * <p>운전현황분석-9번섹션 PLAN1 (2026-05-21) — 본 Repository 는 조회 전용 (테스트 fixture INSERT 외).
 * 1시간 윈도우 DISTINCT ON 단일 흐름은 {@link TagPredcLatestCustomRepository} 에서 제공한다.</p>
 *
 * <p>섹션 7 {@link TagPredictionRepository} 와 동일 엔티티 ({@link TagPrediction}) 를 가리키나, 본 사이클은
 * 단순 최신값 조회 (DISTINCT ON + 1시간 윈도우) 만 책임지며 7번 섹션의 근접 매칭 (LATERAL) 책임을
 * 침범하지 않는다 (PLAN1 §Repository 신규 — 옵션 A 결정).</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 의 논리 참조. 마스터 결합 조회는 Service 계층에서 별도로 조합한다.</p>
 */
public interface TagPredcLatestRepository
        extends JpaRepository<TagPrediction, TagPredictionId>, TagPredcLatestCustomRepository {
}
