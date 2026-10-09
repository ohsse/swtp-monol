package com.mo.swtp.raw.repository;

import com.mo.swtp.raw.domain.RawData;
import com.mo.swtp.raw.domain.RawDataId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SCADA 원시 데이터 유출 시계열 JPA 리포지토리 — 운전현황분석 7번 섹션.
 *
 * <p>{@link RawData} 의 단순 CRUD 를 담당한다. 복합 PK 는 {@link RawDataId} ({@code rawdata_id, acq_dtm}).
 * 본 Repository 는 조회 전용 (테스트 fixture INSERT 외). 시점 범위 유출 시계열 단일 흐름은
 * {@link RawDataOutflowCustomRepository} 에서 제공한다.</p>
 *
 * <p>5번 섹션 {@link RawDataRepository} (DISTINCT ON 최신값 + 시점 범위 공용) 와 동일 엔티티
 * ({@link RawData}) 를 가리키나, 7번 섹션은 5·10번 Repository 자산을 재사용하지 않고 별도 Repository 로
 * 분리한다 (사용자 결정 2026-06-01 "사이클 독립성 우선" · 사용자 메모리 "사이클 간 자산 자동 원용 금지" 정합).</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 의 논리 참조. 마스터 결합 조회는 Service 계층에서 별도로 조합한다.</p>
 */
public interface RawDataOutflowRepository
        extends JpaRepository<RawData, RawDataId>, RawDataOutflowCustomRepository {
}
