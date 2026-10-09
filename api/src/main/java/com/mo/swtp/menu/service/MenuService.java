package com.mo.swtp.menu.service;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.menu.domain.Menu;
import com.mo.swtp.menu.domain.MenuRole;
import com.mo.swtp.menu.dto.MenuUpsertDto;
import com.mo.swtp.menu.exception.MenuErrorCode;
import com.mo.swtp.menu.repository.MenuRepository;
import com.mo.swtp.menu.repository.MenuRoleRepository;
import com.mo.swtp.user.domain.UserRole;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한 메뉴 CRUD 서비스 (ADMIN-only).
 *
 * <p>자기참조 깊이 {@link #MAX_MENU_DEPTH} 제한 + 부모 활성 사전 검증 + 자식 활성 시 비활성화 차단을
 * 애플리케이션 레벨에서 강제한다.</p>
 *
 * <p>매핑 갱신은 INSERT/DELETE 전용 패턴 (`menu_role_r` BaseEntity 미상속 B안 정합) — DELETE 후
 * {@code saveAll} 배치 INSERT 로 처리 (PLAN1 DBA 검토 권고).</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MenuService {

    /** 자기참조 메뉴 트리 깊이 제한 (ANALYZE1 안건 5) */
    private static final int MAX_MENU_DEPTH = 3;

    private final MenuRepository menuRepository;
    private final MenuRoleRepository menuRoleRepository;

    /**
     * 신규 메뉴를 생성한다.
     *
     * @throws RestApiException DUPLICATE_MENU_NM     — 메뉴명 중복
     * @throws RestApiException INVALID_PARENT_MENU    — 부모가 비활성
     * @throws RestApiException MENU_DEPTH_EXCEEDED    — 깊이 N=3 초과
     */
    @Transactional
    public Menu createMenu(MenuUpsertDto dto) {
        if (menuRepository.existsByMenuNm(dto.getMenuNm())) {
            throw new RestApiException(MenuErrorCode.DUPLICATE_MENU_NM);
        }
        validateParent(dto.getParentMenuId());
        Menu menu = Menu.create(
                dto.getMenuNm(), dto.getMenuUrl(), dto.getMenuDesc(),
                dto.getDispOrd(), dto.getParentMenuId());
        return menuRepository.save(menu);
    }

    /**
     * 메뉴 정보를 수정한다. null 필드는 유지된다.
     */
    @Transactional
    public void changeMenu(String menuId, MenuUpsertDto dto) {
        Menu menu = findMenuOrThrow(menuId);
        if (dto.getParentMenuId() != null
                && !dto.getParentMenuId().equals(menu.getParentMenuId())) {
            validateParent(dto.getParentMenuId());
        }
        if (dto.getMenuNm() != null && !dto.getMenuNm().equals(menu.getMenuNm())
                && menuRepository.existsByMenuNm(dto.getMenuNm())) {
            throw new RestApiException(MenuErrorCode.DUPLICATE_MENU_NM);
        }
        menu.changeInfo(
                dto.getMenuNm(), dto.getMenuUrl(), dto.getMenuDesc(),
                dto.getDispOrd(), dto.getParentMenuId());
    }

    /**
     * 메뉴를 비활성화 (논리 삭제) 한다.
     *
     * @throws RestApiException MENU_HAS_ACTIVE_CHILDREN — 자식 활성 메뉴 존재 시 차단
     */
    @Transactional
    public void deactivateMenu(String menuId) {
        Menu menu = findMenuOrThrow(menuId);
        long activeChildren = menuRepository.countByParentMenuIdAndUseYn(menuId, YnType.Y);
        if (activeChildren > 0) {
            throw new RestApiException(MenuErrorCode.MENU_HAS_ACTIVE_CHILDREN);
        }
        menu.deactivate();
    }

    /**
     * 메뉴-권한 매핑을 일괄 교체한다 (DELETE 후 saveAll 배치 INSERT).
     */
    @Transactional
    public void changeMenuRoles(String menuId, List<UserRole> userRoles) {
        Menu menu = findMenuOrThrow(menuId);
        menuRoleRepository.deleteByIdMenuId(menuId);
        List<MenuRole> mappings = userRoles.stream()
                .map(role -> MenuRole.create(menu, role))
                .toList();
        menuRoleRepository.saveAll(mappings);
    }

    /**
     * 메뉴 ID 로 조회 — 미존재 시 {@link MenuErrorCode#MENU_NOT_FOUND}.
     */
    public Menu findMenuOrThrow(String menuId) {
        return menuRepository.findById(menuId)
                .orElseThrow(() -> new RestApiException(MenuErrorCode.MENU_NOT_FOUND));
    }

    private void validateParent(String parentMenuId) {
        if (parentMenuId == null) {
            return;
        }
        Menu parent = findMenuOrThrow(parentMenuId);
        if (parent.getUseYn() == YnType.N) {
            throw new RestApiException(MenuErrorCode.INVALID_PARENT_MENU);
        }
        int parentDepth = computeDepth(parent);
        if (parentDepth + 1 > MAX_MENU_DEPTH) {
            throw new RestApiException(MenuErrorCode.MENU_DEPTH_EXCEEDED);
        }
    }

    private int computeDepth(Menu menu) {
        int depth = 1;
        String currentParentId = menu.getParentMenuId();
        while (currentParentId != null) {
            depth++;
            if (depth > MAX_MENU_DEPTH) {
                return depth;
            }
            Menu current = menuRepository.findById(currentParentId)
                    .orElseThrow(() -> new RestApiException(MenuErrorCode.MENU_NOT_FOUND));
            currentParentId = current.getParentMenuId();
        }
        return depth;
    }
}
