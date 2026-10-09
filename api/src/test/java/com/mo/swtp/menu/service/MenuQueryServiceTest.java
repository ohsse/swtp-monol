package com.mo.swtp.menu.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.menu.domain.Menu;
import com.mo.swtp.menu.dto.MenuTreeDto;
import com.mo.swtp.menu.repository.MenuRepository;
import com.mo.swtp.user.domain.UserRole;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link MenuQueryService} 단위 테스트 — 권한별 트리 빌드 알고리즘 검증.
 */
@ExtendWith(MockitoExtension.class)
class MenuQueryServiceTest {

    @Mock private MenuRepository menuRepository;
    @InjectMocks private MenuQueryService menuQueryService;

    @Test
    void 권한별_평탄_목록을_트리로_빌드한다() {
        Menu root = menuFixture("root", "운영", null);
        Menu child1 = menuFixture("c1", "공지", "root");
        Menu child2 = menuFixture("c2", "FAQ", "root");
        given(menuRepository.findActiveMenusByRole(UserRole.ADMIN))
                .willReturn(List.of(root, child1, child2));

        List<MenuTreeDto> tree = menuQueryService.findMenuTreeByRole(UserRole.ADMIN);

        assertThat(tree).hasSize(1);
        MenuTreeDto rootDto = tree.get(0);
        assertThat(rootDto.getMenuId()).isEqualTo("root");
        assertThat(rootDto.getChildren()).hasSize(2);
        assertThat(rootDto.getChildren()).extracting(MenuTreeDto::getMenuId)
                .containsExactly("c1", "c2");
    }

    @Test
    void ADMIN_과_USER_는_각각의_매핑된_트리만_조회한다() {
        Menu adminOnly = menuFixture("admin-1", "관리", null);
        given(menuRepository.findActiveMenusByRole(UserRole.ADMIN))
                .willReturn(List.of(adminOnly));
        given(menuRepository.findActiveMenusByRole(UserRole.USER))
                .willReturn(List.of());

        List<MenuTreeDto> adminTree = menuQueryService.findMenuTreeByRole(UserRole.ADMIN);
        List<MenuTreeDto> userTree = menuQueryService.findMenuTreeByRole(UserRole.USER);

        assertThat(adminTree).hasSize(1).extracting(MenuTreeDto::getMenuId).containsExactly("admin-1");
        assertThat(userTree).isEmpty();
    }

    @Test
    void 부모가_결과집합에_없으면_고립_노드를_root_로_승격() {
        Menu orphan = menuFixture("orphan", "고립자식", "missing-parent");
        given(menuRepository.findActiveMenusByRole(UserRole.USER))
                .willReturn(List.of(orphan));

        List<MenuTreeDto> tree = menuQueryService.findMenuTreeByRole(UserRole.USER);

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getMenuId()).isEqualTo("orphan");
    }

    @Test
    void 전체_트리_조회는_findByUseYnOrderByDispOrdAsc_사용() {
        Menu root = menuFixture("root", "전체", null);
        given(menuRepository.findByUseYnOrderByDispOrdAsc(YnType.Y))
                .willReturn(List.of(root));

        List<MenuTreeDto> tree = menuQueryService.findFullMenuTree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getMenuId()).isEqualTo("root");
    }

    private Menu menuFixture(String menuId, String menuNm, String parentMenuId) {
        Menu menu = Menu.create(menuNm, "/url", null, 1, parentMenuId);
        try {
            java.lang.reflect.Field idField = Menu.class.getDeclaredField("menuId");
            idField.setAccessible(true);
            idField.set(menu, menuId);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("test fixture setup failure", e);
        }
        return menu;
    }
}
