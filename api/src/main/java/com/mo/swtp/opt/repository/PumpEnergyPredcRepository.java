package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.domain.TagPrediction;
import com.mo.swtp.opt.domain.TagPredictionId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 시설 펌프 전력량 예측 JPA 리포지토리 (전력피크분석 4번섹션 기능2).
 *
 * <p>{@link TagPrediction} ({@code predc_1m_h}) 의 단순 CRUD 를 담당한다. 복합 PK 는 {@link TagPredictionId}
 * ({@code predc_id, predc_dtm}). 본 사이클은 조회 전용 — 1시간 버킷별 차분 집계는
 * {@link PumpEnergyPredcCustomRepository#findEnergyDeltaBuckets} 에서 제공한다.</p>
 *
 * <p>동일 엔티티를 가리키는 2번섹션 {@link PeakPredcRepository} (분별 합산 임계 초과 시각) · 10번섹션
 * {@link TagPredcRangeRepository} (시점 범위 시계열 전체 조회) 와 책임을 분리한다 — 본 사이클은 펌프별
 * 버킷 차분 후 시설 합산 책임만 가지며 타 섹션 책임을 침범하지 않는다 (사이클 간 자산 자동 원용 금지 정합).</p>
 *
 * <p>재사용 스코프 확장 (전력피크분석-5번섹션 PLAN1, 2026-06-05) — {@link PumpEnergyPredcCustomRepository#findEnergyDeltaBuckets}
 * 의 PWQ 버킷 차분 집계는 {@code tag_srl_no IN} 임의 목록에 도메인 중립으로 동작하므로, 5번섹션
 * {@code PeakEnergyTrendService} 가 시설 단위(4번섹션) 가 아닌 시스템 전역 PWQ 예측 추세 산정 입력으로 동일
 * 메서드를 재사용한다. 코드 시그니처·SQL 불변 — 스코프 서술만 보강 (재사용 이력 추적).</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 논리 참조. 마스터 결합 조회는 Service 계층에서 별도로 조합한다.</p>
 */
public interface PumpEnergyPredcRepository
        extends JpaRepository<TagPrediction, TagPredictionId>, PumpEnergyPredcCustomRepository {
}
