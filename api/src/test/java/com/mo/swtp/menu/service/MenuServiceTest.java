package com.mo.swtp.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;

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
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MenuService} 단위 테스트 — Mockito 격리.
 *
 * <p>시나리오: 생성/수정/비활성/매핑 갱신 정상 + 깊이 초과·자식 활성·중복명·미존재·비활성 부모 차단.</p>
 */
@ExtendWith(MockitoExtension.class)
class MenuServiceTest {

    @Mock private MenuRepository menuRepository;
    @Mock private MenuRoleRepository menuRoleRepository;
    @InjectMocks private MenuService menuService;

    @Test
    void 최상위_메뉴_생성_정상() {
        MenuUpsertDto dto = buildDto("공지사항", "/notices", null);
        given(menuRepository.existsByMenuNm("공지사항")).willReturn(false);
        given(menuRepository.save(any(Menu.class))).willAnswer(inv -> inv.getArgument(0));

        Menu created = menuService.createMenu(dto);

        assertThat(created.getMenuNm()).isEqualTo("공지사항");
        assertThat(created.getParentMenuId()).isNull();
        assertThat(created.getUseYn()).isEqualTo(YnType.Y);
        then(menuRepository).should().save(any(Menu.class));
    }

    @Test
    void 자식_메뉴_생성_정상() {
        Menu parent = createMenuWithId("parent-001", "운영", null, YnType.Y);
        MenuUpsertDto dto = buildDto("자식메뉴", "/child", "parent-001");
        given(menuRepository.existsByMenuNm("자식메뉴")).willReturn(false);
        given(menuRepository.findById("parent-001")).willReturn(Optional.of(parent));
        given(menuRepository.save(any(Menu.class))).willAnswer(inv -> inv.getArgument(0));

        Menu created = menuService.createMenu(dto);

        assertThat(created.getParentMenuId()).isEqualTo("parent-001");
    }

    @Test
    void 깊이_초과_시_MENU_DEPTH_EXCEEDED_예외() {
        // 깊이 3 메뉴를 부모로 지정 → 새 메뉴는 깊이 4 시도
        Menu lv3 = createMenuWithId("lv3", "L3", "lv2", YnType.Y);
        Menu lv2 = createMenuWithId("lv2", "L2", "lv1", YnType.Y);
        Menu lv1 = createMenuWithId("lv1", "L1", null, YnType.Y);
        MenuUpsertDto dto = buildDto("L4", "/lv4", "lv3");
        given(menuRepository.existsByMenuNm("L4")).willReturn(false);
        given(menuRepository.findById("lv3")).willReturn(Optional.of(lv3));
        given(menuRepository.findById("lv2")).willReturn(Optional.of(lv2));
        given(menuRepository.findById("lv1")).willReturn(Optional.of(lv1));

        assertThatThrownBy(() -> menuService.createMenu(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(MenuErrorCode.MENU_DEPTH_EXCEEDED);
        then(menuRepository).should(never()).save(any(Menu.class));
    }

    @Test
    void 자식_활성_메뉴_존재_시_부모_비활성화_차단() {
        Menu parent = createMenuWithId("parent-001", "운영", null, YnType.Y);
        given(menuRepository.findById("parent-001")).willReturn(Optional.of(parent));
        given(menuRepository.countByParentMenuIdAndUseYn("parent-001", YnType.Y)).willReturn(2L);

        assertThatThrownBy(() -> menuService.deactivateMenu("parent-001"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(MenuErrorCode.MENU_HAS_ACTIVE_CHILDREN);
        assertThat(parent.getUseYn()).isEqualTo(YnType.Y);
    }

    @Test
    void 자식_없는_메뉴_비활성화_정상() {
        Menu menu = createMenuWithId("menu-001", "공지", null, YnType.Y);
        given(menuRepository.findById("menu-001")).willReturn(Optional.of(menu));
        given(menuRepository.countByParentMenuIdAndUseYn("menu-001", YnType.Y)).willReturn(0L);

        menuService.deactivateMenu("menu-001");

        assertThat(menu.getUseYn()).isEqualTo(YnType.N);
    }

    @Test
    void 메뉴_권한_매핑_일괄_갱신은_삭제후_saveAll() {
        Menu menu = createMenuWithId("menu-001", "공지", null, YnType.Y);
        given(menuRepository.findById("menu-001")).willReturn(Optional.of(menu));

        menuService.changeMenuRoles("menu-001", List.of(UserRole.ADMIN, UserRole.USER));

        then(menuRoleRepository).should().deleteByIdMenuId("menu-001");
        then(menuRoleRepository).should(times(1)).saveAll(anyList());
    }

    @Test
    void 메뉴_수정_시_부모_변경하면_깊이_재검증() {
        Menu menu = createMenuWithId("menu-001", "공지", null, YnType.Y);
        Menu newParent = createMenuWithId("new-parent", "신규부모", null, YnType.Y);
        given(menuRepository.findById("menu-001")).willReturn(Optional.of(menu));
        given(menuRepository.findById("new-parent")).willReturn(Optional.of(newParent));

        MenuUpsertDto dto = new MenuUpsertDto();
        dto.setParentMenuId("new-parent");
        menuService.changeMenu("menu-001", dto);

        assertThat(menu.getParentMenuId()).isEqualTo("new-parent");
    }

    @Test
    void 미존재_메뉴_조회_시_MENU_NOT_FOUND_예외() {
        given(menuRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> menuService.findMenuOrThrow("missing"))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(MenuErrorCode.MENU_NOT_FOUND);
    }

    @Test
    void 중복_메뉴명_생성_시_DUPLICATE_MENU_NM_예외() {
        MenuUpsertDto dto = buildDto("기존메뉴", "/existing", null);
        given(menuRepository.existsByMenuNm("기존메뉴")).willReturn(true);

        assertThatThrownBy(() -> menuService.createMenu(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(MenuErrorCode.DUPLICATE_MENU_NM);
        then(menuRepository).should(never()).save(any(Menu.class));
    }

    @Test
    void 비활성_부모_지정_시_INVALID_PARENT_MENU_예외() {
        Menu inactiveParent = createMenuWithId("inactive", "비활성", null, YnType.N);
        MenuUpsertDto dto = buildDto("자식", "/child", "inactive");
        given(menuRepository.existsByMenuNm("자식")).willReturn(false);
        given(menuRepository.findById("inactive")).willReturn(Optional.of(inactiveParent));

        assertThatThrownBy(() -> menuService.createMenu(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(MenuErrorCode.INVALID_PARENT_MENU);
    }

    private MenuUpsertDto buildDto(String menuNm, String menuUrl, String parentMenuId) {
        MenuUpsertDto dto = new MenuUpsertDto();
        dto.setMenuNm(menuNm);
        dto.setMenuUrl(menuUrl);
        dto.setMenuDesc(null);
        dto.setDispOrd(1);
        dto.setParentMenuId(parentMenuId);
        return dto;
    }

    private Menu createMenuWithId(String menuId, String menuNm, String parentMenuId, YnType useYn) {
        // Menu 의 정적 팩토리는 menuId null 로 생성. 테스트는 영속화된 상태 모사 위해 reflection 또는
        // 직접 생성자 접근이 필요하지만, AccessLevel.PRIVATE 이므로 reflection 우회 — 간단히 정적 팩토리로 생성 후
        // PK 가 필요한 부분만 mock 응답으로 처리한다.
        Menu menu = Menu.create(menuNm, "/url", null, 1, parentMenuId);
        try {
            java.lang.reflect.Field idField = Menu.class.getDeclaredField("menuId");
            idField.setAccessible(true);
            idField.set(menu, menuId);
            java.lang.reflect.Field useYnField = Menu.class.getDeclaredField("useYn");
            useYnField.setAccessible(true);
            useYnField.set(menu, useYn);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("test fixture setup failure", e);
        }
        return menu;
    }
}
