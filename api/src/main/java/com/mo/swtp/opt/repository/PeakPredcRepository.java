package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.TagPrediction;
import com.mo.swtp.opt.domain.TagPredictionId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 전력피크 예상시간 JPA 리포지토리 (전력피크분석 2번섹션 지표 ④).
 *
 * <p>{@link TagPrediction} ({@code predc_1m_h}) 의 단순 CRUD 를 담당한다. 복합 PK 는 {@link TagPredictionId}
 * ({@code predc_id, predc_dtm}). 본 사이클은 조회 전용 — 임계 초과 최근접 시각 산정은
 * {@link PeakPredcCustomRepository#findEarliestPredcDtmOverTarget} 에서 제공한다.</p>
 *
 * <p>10번 섹션 {@link TagPredcRangeRepository} (시점 범위 시계열 전체 조회) 와 동일 엔티티를 가리키나,
 * 본 사이클은 GROUP BY+HAVING 임계 초과 시각 1건 책임만 가지며 10번 섹션의 범위 시계열 책임을 침범하지 않는다
 * (사이클 간 자산 자동 원용 금지 정합).</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 논리 참조. 마스터 결합 조회는 Service 계층에서 별도로 조합한다.</p>
 */
public interface PeakPredcRepository
        extends JpaRepository<TagPrediction, TagPredictionId>, PeakPredcCustomRepository {
}
