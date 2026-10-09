package com.mo.swtp.menu.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.menu.domain.Menu;
import com.mo.swtp.menu.dto.MenuTreeDto;
import com.mo.swtp.menu.repository.MenuRepository;
import com.mo.swtp.user.domain.UserRole;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한별 메뉴 트리 조회 서비스 (단일 SELECT + 메모리 트리 빌드 패턴 — ANALYZE1 안건 11).
 *
 * <p>로그인 응답 ({@code LoginFacadeService}) + GET /api/menus/me + ADMIN 전체 조회 공용.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuQueryService {

    private final MenuRepository menuRepository;

    /**
     * 권한에 매핑된 활성 메뉴 트리를 반환한다 (로그인 응답 + GET /menus/me).
     *
     * @param userRole 사용자 권한
     * @return 트리 빌드된 최상위 메뉴 리스트 (children 재귀)
     */
    public List<MenuTreeDto> findMenuTreeByRole(UserRole userRole) {
        List<Menu> activeMenus = menuRepository.findActiveMenusByRole(userRole);
        return buildTree(activeMenus);
    }

    /**
     * 전체 활성 메뉴 트리 (ADMIN 관리 화면용).
     *
     * @return 권한 무관 전체 활성 메뉴 트리
     */
    public List<MenuTreeDto> findFullMenuTree() {
        List<Menu> activeMenus = menuRepository.findByUseYnOrderByDispOrdAsc(YnType.Y);
        return buildTree(activeMenus);
    }

    /**
     * 평탄 메뉴 목록을 {@code parent_menu_id} 기준 트리로 빌드한다.
     *
     * <p>부모가 결과 집합에 없는 경우 (비활성/권한외) 해당 노드는 root 로 승격하여 고립 회피한다.
     * 정상 시나리오에서는 {@code MenuService.deactivateMenu()} 의 자식 활성 검증으로 부모 고립이 방지된다.</p>
     */
    private List<MenuTreeDto> buildTree(List<Menu> activeMenus) {
        Map<String, MenuTreeDto> dtoMap = new LinkedHashMap<>();
        for (Menu menu : activeMenus) {
            dtoMap.put(menu.getMenuId(), MenuTreeDto.from(menu));
        }

        List<MenuTreeDto> roots = new ArrayList<>();
        for (Menu menu : activeMenus) {
            MenuTreeDto current = dtoMap.get(menu.getMenuId());
            String parentId = menu.getParentMenuId();
            if (parentId == null || !dtoMap.containsKey(parentId)) {
                roots.add(current);
            } else {
                dtoMap.get(parentId).addChild(current);
            }
        }
        return roots;
    }
}
