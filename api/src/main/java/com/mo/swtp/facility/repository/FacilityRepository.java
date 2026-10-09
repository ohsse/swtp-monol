package com.mo.swtp.facility.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import java.util.List;
import org.hibernate.annotations.BatchSize;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 시설 부모 마스터 JPA 리포지토리.
 *
 * <p>JPA JOINED 다형성 부모 ({@link Facility}) 의 단순 CRUD 를 담당한다.
 * 자식 종류별 도메인 룰이 다른 시나리오에서는 {@link #findByFacilityType(FacilityType)} 으로
 * 자식 종류 필터를 강제 적용한다 — {@code .claude/rules/entity-patterns.md} §JPA JOINED 도메인 룰
 * §{@code facility_type_cd} 필터 강제 정합.</p>
 *
 * <p>N+1 방지: 부모 다형성 목록 조회 시 자식 테이블 LEFT OUTER JOIN 이 자식 종류 수만큼 발생할 수 있으므로
 * {@link BatchSize} 적용 (PLAN1 §Phase 3, {@code db/query-tuning.md §2} 정합).</p>
 *
 * <p>도메인 시나리오상 다형성 전체 조회 빈도는 "드물거나 없음" 이지만 안전을 위해 첫 작성부터 적용한다.</p>
 */
public interface FacilityRepository extends JpaRepository<Facility, String>, FacilityCustomRepository {

    /**
     * 자식 종류별 시설 목록 조회 — 부모 다형성 전체 조회 시 도메인 룰 필터 강제.
     *
     * @param facilityType 시설 유형 (PWTF/DWT/RSV)
     * @return 해당 종류 시설 목록
     */
    @BatchSize(size = 100)
    List<Facility> findByFacilityType(FacilityType facilityType);

    /**
     * 시설명 존재 여부 — UNIQUE 시스템 전체 보장 (사용자 결정 핵심).
     *
     * @param facilityNm 시설명
     * @return 1건 이상 존재 시 true
     */
    boolean existsByFacilityNm(String facilityNm);

    /**
     * 시설명 존재 여부 — 자기 자신을 제외한 UNIQUE 검사 (수정 시).
     *
     * <p>시설물관리기능 PLAN1 (2026-05-11) — PUT 수정 시 시설명 변경이 다른 시설과 충돌하는지만 검사.
     * 자기 자신은 같은 시설명 유지가 정상 케이스이므로 제외.</p>
     *
     * @param facilityNm 시설명
     * @param facilityId 자기 자신 제외 대상 시설 ID
     * @return 자기 자신을 제외하고 1건 이상 존재 시 true
     */
    boolean existsByFacilityNmAndFacilityIdNot(String facilityNm, String facilityId);

    /**
     * 부모 시설의 활성 자식 시설을 자식 종류 필터 + {@code disp_ord} ASC 정렬로 조회한다.
     *
     * <p>송수펌프제어분석 §3 — 활성 정수지의 자식 DWT 목록 조회 데이터원 (PLAN1 §호출 흐름 §3).</p>
     *
     * @param parentFacilityId 부모 시설 ID
     * @param facilityType     자식 종류 (예: {@link FacilityType#DWT})
     * @param useYn            사용 여부 (보통 {@link YnType#Y})
     * @return {@code disp_ord} ASC 정렬 자식 시설 목록
     */
    @BatchSize(size = 100)
    List<Facility> findByParentFacilityIdAndFacilityTypeAndUseYnOrderByDispOrdAsc(
            String parentFacilityId, FacilityType facilityType, YnType useYn);

    /**
     * 부모 시설 IN + 활성 자식 시설을 한 번의 SQL 로 조회한다 (재귀 하위 BFS 한 단계).
     *
     * <p>운전현황분석 8번 섹션 — 활성 루트 시설을 상위로 한 재귀 하위 트리를 BFS 로 도출할 때, 한 레벨의 부모
     * ID 묶음에 대한 활성 자식을 한 번에 가져온다 (레벨당 SQL 1회 — 레벨 수 ≪ 노드 수). 자식 종류 필터는
     * 적용하지 않는다 — 표출대상(PWTF·POINT)·수위 소스(DWT)·중간 경로 시설을 모두 수집한 뒤 Service 가
     * 인메모리로 분류한다 (PLAN1 §재귀 하위 도출).</p>
     *
     * @param parentFacilityIds 부모 시설 ID 묶음 (한 BFS 레벨)
     * @param useYn             사용 여부 (보통 {@link YnType#Y})
     * @return 활성 자식 시설 목록 (빈 리스트 가능)
     */
    @BatchSize(size = 100)
    List<Facility> findByParentFacilityIdInAndUseYn(List<String> parentFacilityIds, YnType useYn);

    /**
     * 자식 종류 IN + 활성 시설 목록을 표시 순서 → 시설명 정렬로 조회한다.
     *
     * <p>시설별사용량-2번섹션 — 운영시설({@code facility_type_cd ∈ OPERATION 그룹 8종} + {@code use_yn = Y}) 카드를
     * {@code disp_ord ASC}, 동률 시 {@code facility_nm ASC} 로 조회한다 (PLAN1 §Service 흐름 3). 자식 종류 집합은
     * {@code FacilityType.getGroup() == OPERATION} 필터로 Service 가 도출하므로 부모 다형성 전체 조회가 아닌
     * 운영시설 종류만 명시 — {@code facility_type_cd} 필터 강제 정합 ({@code .claude/rules/entity-patterns.md}
     * §JPA JOINED 도메인 룰).</p>
     *
     * @param facilityTypes 자식 종류 집합 (운영시설 8종)
     * @param useYn         사용 여부 (보통 {@link YnType#Y})
     * @return {@code disp_ord} ASC → {@code facility_nm} ASC 정렬 시설 목록
     */
    @BatchSize(size = 100)
    List<Facility> findByFacilityTypeInAndUseYnOrderByDispOrdAscFacilityNmAsc(
            List<FacilityType> facilityTypes, YnType useYn);
}
