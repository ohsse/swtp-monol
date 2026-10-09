package com.mo.swtp.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.mo.swtp.auth.dto.LoginResponseDto;
import com.mo.swtp.auth.dto.TokenResponseDto;
import com.mo.swtp.menu.domain.Menu;
import com.mo.swtp.menu.dto.MenuTreeDto;
import com.mo.swtp.menu.service.MenuQueryService;
import com.mo.swtp.user.domain.UserRole;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link LoginFacadeService} 단위 테스트 — 토큰 + 메뉴 트리 조립 검증.
 */
@ExtendWith(MockitoExtension.class)
class LoginFacadeServiceTest {

    @Mock private AuthService authService;
    @Mock private MenuQueryService menuQueryService;
    @InjectMocks private LoginFacadeService loginFacadeService;

    @Test
    void 로그인_응답_body_에_권한별_메뉴_트리가_포함된다() {
        TokenResponseDto tokens = TokenResponseDto.builder()
                .accessToken("at")
                .refreshToken("rt")
                .accessExprDtm(LocalDateTime.of(2026, 5, 6, 12, 0))
                .refreshExprDtm(LocalDateTime.of(2026, 5, 13, 12, 0))
                .role(UserRole.ADMIN)
                .build();
        Menu menu = Menu.create("관리", "/admin", null, 1, null);
        MenuTreeDto adminMenu = MenuTreeDto.from(menu);
        given(authService.login("admin", "pw")).willReturn(tokens);
        given(menuQueryService.findMenuTreeByRole(UserRole.ADMIN)).willReturn(List.of(adminMenu));

        LoginResponseDto response = loginFacadeService.login("admin", "pw");

        assertThat(response.getUserId()).isEqualTo("admin");
        assertThat(response.getAccessToken()).isEqualTo("at");
        assertThat(response.getRefreshToken()).isEqualTo("rt");
        assertThat(response.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(response.getMenus()).hasSize(1);
        assertThat(response.getMenus().get(0).getMenuNm()).isEqualTo("관리");
    }

    @Test
    void ADMIN_과_USER_는_권한별로_다른_메뉴_트리를_받는다() {
        TokenResponseDto adminTokens = tokensFor(UserRole.ADMIN);
        TokenResponseDto userTokens = tokensFor(UserRole.USER);
        MenuTreeDto adminMenu = MenuTreeDto.from(Menu.create("관리", "/admin", null, 1, null));
        MenuTreeDto userMenu = MenuTreeDto.from(Menu.create("조회", "/view", null, 1, null));
        given(authService.login("admin", "pw")).willReturn(adminTokens);
        given(authService.login("user", "pw")).willReturn(userTokens);
        given(menuQueryService.findMenuTreeByRole(UserRole.ADMIN)).willReturn(List.of(adminMenu));
        given(menuQueryService.findMenuTreeByRole(UserRole.USER)).willReturn(List.of(userMenu));

        LoginResponseDto adminResponse = loginFacadeService.login("admin", "pw");
        LoginResponseDto userResponse = loginFacadeService.login("user", "pw");

        assertThat(adminResponse.getUserId()).isEqualTo("admin");
        assertThat(userResponse.getUserId()).isEqualTo("user");
        assertThat(adminResponse.getMenus().get(0).getMenuNm()).isEqualTo("관리");
        assertThat(userResponse.getMenus().get(0).getMenuNm()).isEqualTo("조회");
        assertThat(adminResponse.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(userResponse.getRole()).isEqualTo(UserRole.USER);
    }

    private TokenResponseDto tokensFor(UserRole role) {
        return TokenResponseDto.builder()
                .accessToken("at-" + role.name())
                .refreshToken("rt-" + role.name())
                .accessExprDtm(LocalDateTime.of(2026, 5, 6, 12, 0))
                .refreshExprDtm(LocalDateTime.of(2026, 5, 13, 12, 0))
                .role(role)
                .build();
    }
}
