package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.TagPrediction;
import com.mo.swtp.opt.domain.TagPredictionId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 예측 시계열 유출 JPA 리포지토리 — 운전현황분석 7번 섹션.
 *
 * <p>{@link TagPrediction} 의 단순 CRUD 를 담당한다. 복합 PK 는 {@link TagPredictionId}
 * ({@code predc_id, predc_dtm}). 본 Repository 는 조회 전용 (테스트 fixture INSERT 외). 시점 범위 유출 예측
 * 시계열 단일 흐름은 {@link TagPredcOutflowCustomRepository} 에서 제공한다.</p>
 *
 * <p>10번 섹션 {@link TagPredcRangeRepository} (시점 범위 공용) · 9번 섹션 {@link TagPredcLatestRepository}
 * (단일 시점 DISTINCT ON) 와 동일 엔티티 ({@link TagPrediction}) 를 가리키나, 7번 섹션은 5·10번 Repository
 * 자산을 재사용하지 않고 별도 Repository 로 분리한다 (사용자 결정 2026-06-01 "사이클 독립성 우선" · 사용자 메모리
 * "사이클 간 자산 자동 원용 금지" 정합).</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 의 논리 참조. 마스터 결합 조회는 Service 계층에서 별도로 조합한다.</p>
 */
public interface TagPredcOutflowRepository
        extends JpaRepository<TagPrediction, TagPredictionId>, TagPredcOutflowCustomRepository {
}
