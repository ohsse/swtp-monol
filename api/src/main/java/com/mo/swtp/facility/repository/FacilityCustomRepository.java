package com.mo.swtp.facility.repository;

import com.mo.swtp.facility.domain.Facility;
import com.mo.swtp.facility.domain.enumtype.FacilityType;
import com.mo.swtp.facility.dto.FacilitySearchDto;
import java.util.List;
import java.util.Optional;

/**
 * 시설 커스텀 조회 인터페이스 (Querydsl).
 *
 * <p>송수펌프제어분석 PLAN1 (2026-05-08) 도입 — exists 서브쿼리·정렬 LIMIT 1 등 단순 메서드 명명
 * 규칙으로 표현하기 어려운 조회 메서드를 모은다.</p>
 *
 * <p>도메인 룰: 시설 자식 종류별 도메인 룰이 다른 시나리오는 반드시 {@code facility_type_cd} 필터를
 * 명시한다 ({@code .claude/rules/entity-patterns.md} §JPA JOINED §도메인 룰).</p>
 *
 * <p>"DWT 자식 보유 부모 시설 목록 조회" 메서드는 시설물응답DTO명세 ANALYZE1 (2026-05-12)
 * 안건 4 결정으로 백지화되었다 — pump+AI 백지화 사이클 1 직후 호출자 ({@code PumpControlAnalysisController})
 * 가 사라지면서 고아 상태가 된 자산이다. 향후 재도입 시 reference 패턴 ({@code FacilitySearchDto}
 * 확장 + {@code List<FacilityDto>} 다형 응답) 채택 권고.</p>
 */
public interface FacilityCustomRepository {

    /**
     * 특정 부모 시설에 속한 첫 번째 자식 시설을 자식 종류 필터 + {@code disp_ord} ASC 정렬로 조회한다.
     *
     * <p>송수펌프제어분석 화면 §4 기준배수지 (자식 DWT 중 {@code disp_ord} ASC 1번째) 결정용.
     * 사용자 결정 (2026-05-08): FK 신설 없이 부모 시설별 자식 DWT 중 표시 순서 첫 번째를 기준으로 한다.</p>
     *
     * @param parentFacilityId 부모 시설 ID
     * @param childType        자식 종류 (예: {@link FacilityType#DWT})
     * @return 첫 번째 자식 시설 (없으면 empty)
     */
    Optional<Facility> findFirstChildByParentIdAndType(String parentFacilityId, FacilityType childType);

    /**
     * 시설 목록 조회 — {@code facilityTypeCd} / {@code useYn} 필터 + 정렬.
     *
     * <p>시설물관리기능 PLAN1 (2026-05-11) — 두 필터 모두 NULL 허용. NULL 인 필드는 조건 미적용
     * (전체 반환). 정렬 순서: {@code use_yn} DESC (활성 우선) → {@code disp_ord} ASC →
     * {@code facility_nm} ASC. user_m·tag_m 선례 동일 패턴.</p>
     *
     * @param searchDto 조회 필터 (NULL 허용)
     * @return 정렬된 시설 목록
     */
    List<Facility> findFacilities(FacilitySearchDto searchDto);
}
