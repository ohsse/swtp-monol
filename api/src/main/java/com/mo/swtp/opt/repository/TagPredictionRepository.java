package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.TagPrediction;
import com.mo.swtp.opt.domain.TagPredictionId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * AI 예측 시계열 태그 예측값 JPA 리포지토리.
 *
 * <p>{@link TagPrediction} 의 단순 CRUD 를 담당한다. 복합 PK 는 {@link TagPredictionId}
 * ({@code predc_id, predc_dtm}).</p>
 *
 * <p>본 사이클은 조회 전용 — INSERT 경로 = AI 추론 파이프라인 (사이클 2 결정 대기). 본 JpaRepository 는
 * fixture / 통합 테스트 INSERT 용도로 사용된다 (PLAN1 §2).</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 의 논리 참조이며 본 리포지토리는 마스터 엔티티 조회 메서드를 노출하지 않는다.
 * 마스터 결합 조회는 Service 계층에서 별도로 조합한다.</p>
 *
 * <p>근접매칭 단일 native SQL 흐름은 {@link TagPredictionCustomRepository} 에서 제공한다.</p>
 */
public interface TagPredictionRepository
        extends JpaRepository<TagPrediction, TagPredictionId>, TagPredictionCustomRepository {
}
