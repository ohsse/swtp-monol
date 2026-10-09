package com.mo.swtp.menu.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.auth.guard.RoleGuard;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.menu.dto.MenuRoleUpsertDto;
import com.mo.swtp.menu.dto.MenuTreeDto;
import com.mo.swtp.menu.dto.MenuUpsertDto;
import com.mo.swtp.menu.service.MenuQueryService;
import com.mo.swtp.menu.service.MenuService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 권한 메뉴 관리 API 컨트롤러 (ADMIN 전용).
 *
 * <p>메뉴 등록·수정·비활성화·전체 트리 조회·권한 매핑 갱신을 제공한다.
 * 모든 엔드포인트는 ADMIN 역할만 수행할 수 있다.</p>
 */
@Tag(name = "02. 메뉴 관리")
@RestController
@RequestMapping("/api/menus")
@RequiredArgsConstructor
public class MenuController extends CommonController {

    private final MenuService menuService;
    private final MenuQueryService menuQueryService;
    private final RoleGuard roleGuard;

    @Operation(summary = "메뉴 등록 (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (깊이 초과·비활성 부모)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "409", description = "중복 메뉴명"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<CommonResponseDto<Void>> createMenu(
            @Valid @RequestBody MenuUpsertDto dto,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        menuService.createMenu(dto);
        return getResponseEntity();
    }

    @Operation(summary = "메뉴 수정 (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "메뉴 없음"),
            @ApiResponse(responseCode = "409", description = "중복 메뉴명"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/{menuId}")
    public ResponseEntity<CommonResponseDto<Void>> updateMenu(
            @PathVariable String menuId,
            @Valid @RequestBody MenuUpsertDto dto,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        menuService.changeMenu(menuId, dto);
        return getResponseEntity();
    }

    @Operation(summary = "메뉴 비활성화 (ADMIN 전용, 논리 삭제)",
               description = "자식 활성 메뉴가 존재하면 차단된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "메뉴 없음"),
            @ApiResponse(responseCode = "409", description = "자식 활성 메뉴 존재"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{menuId}")
    public ResponseEntity<CommonResponseDto<Void>> deactivateMenu(
            @PathVariable String menuId,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        menuService.deactivateMenu(menuId);
        return getResponseEntity();
    }

    @Operation(summary = "전체 메뉴 트리 조회 (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<List<MenuTreeDto>>> findFullMenuTree(
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        return getResponseEntity(menuQueryService.findFullMenuTree());
    }

    @Operation(summary = "메뉴-권한 매핑 갱신 (ADMIN 전용)",
               description = "전달된 userRoles 로 매핑을 일괄 교체한다 (DELETE 후 saveAll).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 (빈 권한 목록)"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "메뉴 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping("/{menuId}/roles")
    public ResponseEntity<CommonResponseDto<Void>> changeMenuRoles(
            @PathVariable String menuId,
            @Valid @RequestBody MenuRoleUpsertDto dto,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        menuService.changeMenuRoles(menuId, dto.getUserRoles());
        return getResponseEntity();
    }
}
