package com.mo.swtp.menu.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.auth.exception.AuthErrorCode;
import com.mo.swtp.auth.web.JwtAuthenticationFilter;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.menu.dto.MenuTreeDto;
import com.mo.swtp.menu.service.MenuQueryService;
import com.mo.swtp.user.domain.UserRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 본인 권한 메뉴 트리 조회 API (인증 사용자 전용).
 *
 * <p>frontend 가 라우팅 진입 시 또는 메뉴 변경 알림 수신 후 재조회한다 (ANALYZE1 안건 12 — JWT 페이로드 분리).</p>
 */
@Tag(name = "02-1. 내 메뉴 조회")
@RestController
@RequestMapping("/api/menus/me")
@RequiredArgsConstructor
public class MyMenuController extends CommonController {

    private final MenuQueryService menuQueryService;

    @Operation(summary = "본인 권한에 해당하는 메뉴 트리 조회",
               description = "JWT claims 의 role 을 기준으로 권한별 활성 메뉴 트리를 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<List<MenuTreeDto>>> findMyMenuTree(HttpServletRequest request) {
        UserRole role = extractRole(request);
        return getResponseEntity(menuQueryService.findMenuTreeByRole(role));
    }

    @SuppressWarnings("unchecked")
    private UserRole extractRole(HttpServletRequest request) {
        Map<String, Object> claims =
                (Map<String, Object>) request.getAttribute(JwtAuthenticationFilter.AUTH_CLAIMS_ATTRIBUTE);
        if (claims == null) {
            throw new RestApiException(AuthErrorCode.UNAUTHORIZED);
        }
        Object roleClaim = claims.get("role");
        if (roleClaim == null) {
            throw new RestApiException(AuthErrorCode.UNAUTHORIZED);
        }
        return UserRole.valueOf(roleClaim.toString());
    }
}
