package com.mo.swtp.menu.repository;

import com.mo.swtp.menu.domain.Menu;
import com.mo.swtp.user.domain.UserRole;
import java.util.List;

/**
 * 메뉴 커스텀 리포지토리 — 권한별 활성 메뉴 평탄 조회.
 *
 * <p>트리 빌드는 {@code MenuQueryService} 가 메모리에서 처리한다 (단일 SELECT + 메모리 트리 빌드 패턴 — ANALYZE1 안건 11).</p>
 */
public interface MenuCustomRepository {

    /**
     * 권한에 매핑된 활성 메뉴를 표시 순서로 평탄 조회한다.
     *
     * <p>{@code menu_m JOIN menu_role_r WHERE user_role = ? AND use_yn = 'Y' ORDER BY disp_ord} 단일 SELECT.</p>
     *
     * @param userRole 사용자 권한
     * @return 권한 매핑된 활성 메뉴 목록 (표시 순서 정렬)
     */
    List<Menu> findActiveMenusByRole(UserRole userRole);
}
