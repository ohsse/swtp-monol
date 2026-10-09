package com.mo.swtp.menu.repository;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.menu.domain.Menu;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 메뉴 JPA 리포지토리.
 */
public interface MenuRepository extends JpaRepository<Menu, String>, MenuCustomRepository {

    /**
     * 활성 메뉴 전체를 표시 순서로 조회한다 (ADMIN 관리 화면용 전체 트리).
     *
     * @param useYn 사용 여부 (활성 조회 시 {@link YnType#Y})
     * @return 활성 메뉴 평탄 목록 (표시 순서 정렬)
     */
    List<Menu> findByUseYnOrderByDispOrdAsc(YnType useYn);

    /**
     * 특정 부모 메뉴의 자식 활성 메뉴 개수를 조회한다 (deactivate 사전 검증용).
     *
     * @param parentMenuId 부모 메뉴 ID
     * @param useYn        사용 여부 (활성 자식 검사 시 {@link YnType#Y})
     * @return 자식 활성 메뉴 개수
     */
    long countByParentMenuIdAndUseYn(String parentMenuId, YnType useYn);

    /**
     * 메뉴명 중복 여부 (UNIQUE 검증 — 사전 검사용. UNIQUE 제약은 DB 가 최종 차단).
     *
     * @param menuNm 메뉴명
     * @return 동일 메뉴명 존재 여부
     */
    boolean existsByMenuNm(String menuNm);
}
