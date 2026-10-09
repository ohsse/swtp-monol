package com.mo.swtp.menu.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.mo.swtp.user.domain.UserRole;
import org.junit.jupiter.api.Test;

/**
 * {@link MenuRole} · {@link MenuRoleId} 단위 테스트.
 *
 * <p>복합 PK 의 equals·hashCode 정합성 + 정적 팩토리 검증.</p>
 */
class MenuRoleTest {

    @Test
    void MenuRoleId_같은_값으로_생성하면_equals_true_이다() {
        MenuRoleId id1 = new MenuRoleId("menu-001", UserRole.ADMIN);
        MenuRoleId id2 = new MenuRoleId("menu-001", UserRole.ADMIN);

        assertThat(id1).isEqualTo(id2);
        assertThat(id1.hashCode()).isEqualTo(id2.hashCode());
    }

    @Test
    void MenuRoleId_menuId가_다르면_equals_false_이다() {
        MenuRoleId id1 = new MenuRoleId("menu-001", UserRole.ADMIN);
        MenuRoleId id2 = new MenuRoleId("menu-002", UserRole.ADMIN);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void MenuRoleId_userRole이_다르면_equals_false_이다() {
        MenuRoleId id1 = new MenuRoleId("menu-001", UserRole.ADMIN);
        MenuRoleId id2 = new MenuRoleId("menu-001", UserRole.USER);

        assertThat(id1).isNotEqualTo(id2);
    }

    @Test
    void MenuRole_create_정적_팩토리는_id와_menu를_동기화한다() {
        Menu menu = Menu.create("공지사항", "/notices", null, 1, null);
        // menu.menuId 는 영속화 전이므로 null 이지만, MenuRoleId 가 그 값을 그대로 받음
        MenuRole mapping = MenuRole.create(menu, UserRole.ADMIN);

        assertThat(mapping.getId()).isNotNull();
        assertThat(mapping.getId().getUserRole()).isEqualTo(UserRole.ADMIN);
        assertThat(mapping.getMenu()).isSameAs(menu);
        // rgstr_dtm·rgstr_id 는 영속화 시 AuditingEntityListener 가 주입 (단위 테스트에서는 null)
        assertThat(mapping.getRgstrDtm()).isNull();
        assertThat(mapping.getRgstrId()).isNull();
    }
}
