package com.mo.swtp.menu.repository;

import com.mo.swtp.menu.domain.MenuRole;
import com.mo.swtp.menu.domain.MenuRoleId;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * 메뉴-권한 N:M 매핑 JPA 리포지토리.
 *
 * <p>매핑 행 자체는 INSERT/DELETE 전용 (UPDATE 시나리오 없음). 권한 갱신 시
 * {@link #deleteByIdMenuId(String)} 일괄 삭제 후 {@link #saveAll} 배치 INSERT 패턴 사용.</p>
 */
public interface MenuRoleRepository extends JpaRepository<MenuRole, MenuRoleId> {

    /**
     * 특정 메뉴의 모든 권한 매핑을 조회한다.
     *
     * @param menuId 메뉴 ID
     * @return 해당 메뉴에 매핑된 권한 목록
     */
    List<MenuRole> findByIdMenuId(String menuId);

    /**
     * 특정 메뉴의 모든 권한 매핑을 단일 DELETE 로 일괄 삭제한다 (권한 갱신 1단계).
     *
     * <p>Spring Data 파생 메서드 ({@code SimpleJpaRepository}) 의 SELECT-then-REMOVE
     * N+1 DELETE 패턴을 회피하기 위해 {@code @Modifying @Query} 벌크 DELETE 로 정의한다.
     * 본 메서드는 {@link com.mo.swtp.menu.service.MenuService#changeMenuRoles} 의 일괄
     * 삭제 후 {@code saveAll} 배치 INSERT 패턴 1 단계에서 사용된다.</p>
     *
     * @param menuId 메뉴 ID
     * @return 삭제된 매핑 행 수
     */
    @Modifying
    @Query("DELETE FROM MenuRole mr WHERE mr.id.menuId = :menuId")
    int deleteByIdMenuId(@Param("menuId") String menuId);
}
