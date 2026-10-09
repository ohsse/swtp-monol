package com.mo.swtp.raw.repository;

import com.mo.swtp.raw.domain.RawData;
import com.mo.swtp.raw.domain.RawDataId;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * SCADA 원시 데이터 시계열 JPA 리포지토리.
 *
 * <p>{@link RawData} 의 단순 CRUD 를 담당한다. 복합 PK 는 {@link RawDataId} ({@code rawdata_id, acq_dtm}).</p>
 *
 * <p>시계열 → 마스터 FK 금지 ({@code db/partitioning-and-retention.md §1}) — {@code tag_srl_no} 는
 * {@code tag_m.tag_srl_no} 의 논리 참조이며 본 리포지토리는 마스터 엔티티 조회 메서드를 노출하지 않는다.
 * 마스터 결합 조회는 Service 계층에서 별도로 조합한다.</p>
 *
 * <p>대용량 조회 (페이지네이션·커서 기반)·BRIN 프루닝 활용은 {@code Custom} 인터페이스 + Querydsl 구현으로
 * 분리할 예정 — 본 PLAN 에서는 단순 CRUD 만 노출 (Service/Controller 미구현 범위 외).</p>
 */
public interface RawDataRepository extends JpaRepository<RawData, RawDataId>, RawDataCustomRepository {
}
