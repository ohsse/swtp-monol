package com.mo.swtp.auth.service;

import com.mo.swtp.auth.dto.LoginResponseDto;
import com.mo.swtp.auth.dto.TokenResponseDto;
import com.mo.swtp.menu.dto.MenuTreeDto;
import com.mo.swtp.menu.service.MenuQueryService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 로그인 Facade 서비스 — 토큰 발급 + 권한별 메뉴 트리 조립.
 *
 * <p>{@code AuthService} 가 토큰 발급 (TokenResponseDto 반환) 을 담당하고,
 * 본 Facade 가 {@code MenuQueryService.findMenuTreeByRole} 결과와 결합하여 {@link LoginResponseDto} 를 조립한다.</p>
 *
 * <p>본 분리는 도메인 간 직접 의존 회피 (ANALYZE1 안건 12) 와 응답 DTO 관심사 분리 (TokenResponseDto refresh 전용 유지) 를 위함이다.</p>
 */
@Service
@RequiredArgsConstructor
public class LoginFacadeService {

    private final AuthService authService;
    private final MenuQueryService menuQueryService;

    /**
     * 사용자 로그인을 수행하고 권한별 메뉴 트리를 포함한 응답을 반환한다.
     *
     * @param userId 사용자 ID
     * @param rawPw  평문 비밀번호
     * @return 토큰 + 권한별 메뉴 트리를 포함한 로그인 응답 DTO
     */
    public LoginResponseDto login(String userId, String rawPw) {
        TokenResponseDto tokens = authService.login(userId, rawPw);
        List<MenuTreeDto> menus = menuQueryService.findMenuTreeByRole(tokens.getRole());
        return LoginResponseDto.from(tokens.getTtrylUseYn(),tokens, userId, menus);
    }
}
