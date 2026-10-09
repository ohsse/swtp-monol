package com.mo.swtp.auth.dto;

import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.menu.dto.MenuTreeDto;
import com.mo.swtp.user.domain.UserRole;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

/**
 * 로그인 응답 DTO.
 *
 * <p>사용자 식별자 (userId) + 토큰 정보 (AT/RT + 만료시각 + role) + 권한별 메뉴 트리를 응답 body 로 함께 전달한다.
 * userId 를 응답 body 에 평문 노출하여 프론트엔드의 access token 디코딩 의존성을 제거한다.
 * 메뉴 정보는 JWT 페이로드가 아닌 응답 body 분리 (ANALYZE1 안건 7 — 사용자 사전 결정).</p>
 *
 * <p>refresh 응답은 {@link TokenResponseDto} 가 담당한다.</p>
 */
@Getter
@Builder
@Schema(description = "로그인 응답 DTO (사용자 ID + 토큰 + 권한별 메뉴 트리)")
public class LoginResponseDto {

    @Schema(description = "사용자 ID")
    private String userId;

    @Schema(description = "액세스 토큰 (Bearer)")
    private String accessToken;

    @Schema(description = "리프레시 토큰")
    private String refreshToken;

    @Schema(description = "액세스 토큰 만료 일시")
    private LocalDateTime accessExprDtm;

    @Schema(description = "리프레시 토큰 만료 일시")
    private LocalDateTime refreshExprDtm;

    @Schema(description = "사용자 권한 역할", implementation = UserRole.class)
    private UserRole role;

    @ArraySchema(schema = @Schema(description = "권한별 메뉴 트리", implementation = MenuTreeDto.class))
    private List<MenuTreeDto> menus;

    @Schema(description = "튜토리얼 사용 여부")
    private YnType ttrylUseYn;
    /**
     * {@link TokenResponseDto} 와 사용자 ID, 메뉴 트리를 결합하여 로그인 응답을 생성한다.
     *
     * @param tokens AuthService.login() 결과 (토큰 + 만료시각 + role)
     * @param userId 사용자 ID (응답 body 평문 노출 — token 디코딩 의존성 제거)
     * @param menus  권한별 메뉴 트리
     * @return 로그인 응답 DTO
     */
    public static LoginResponseDto from(YnType ttrylUseYn, TokenResponseDto tokens, String userId, List<MenuTreeDto> menus) {
        return LoginResponseDto.builder()
                .userId(userId)
                .accessToken(tokens.getAccessToken())
                .refreshToken(tokens.getRefreshToken())
                .accessExprDtm(tokens.getAccessExprDtm())
                .refreshExprDtm(tokens.getRefreshExprDtm())
                .role(tokens.getRole())
                .menus(menus)
                .ttrylUseYn(ttrylUseYn)
                .build();
    }
}
