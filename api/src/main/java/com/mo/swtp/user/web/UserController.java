package com.mo.swtp.user.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.auth.guard.RoleGuard;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.user.dto.UserDto;
import com.mo.swtp.user.dto.UserUpsertDto;
import com.mo.swtp.user.service.UserService;
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
 * 사용자 관리 API 컨트롤러.
 *
 * <p>사용자 등록·조회·수정·비활성화를 제공한다.
 * 등록·수정·비활성화는 ADMIN 역할만 수행할 수 있다.</p>
 */
@Tag(name = "01. 사용자 관리")
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController extends CommonController {

    private final UserService userService;
    private final RoleGuard roleGuard;

    @Operation(summary = "사용자 등록 (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "409", description = "중복 사용자 ID"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PostMapping
    public ResponseEntity<CommonResponseDto<Void>> registerUser(
            @Valid @RequestBody UserUpsertDto dto,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        userService.registerUser(dto);
        return getResponseEntity();
    }

    @Operation(summary = "사용자 전체 목록 조회 (ADMIN 전용)",
               description = "활성·비활성 사용자를 모두 반환한다. 정렬은 활성 우선(useYn DESC) 후 사용자 ID 오름차순.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<List<UserDto>>> findAllUsers(HttpServletRequest request) {
        roleGuard.requireAdmin(request);
        List<UserDto> users = userService.findAllUsers().stream()
                .map(UserDto::from)
                .toList();
        return getResponseEntity(users);
    }

    @Operation(summary = "사용자 조회")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "사용자 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping("/{userId}")
    public ResponseEntity<CommonResponseDto<UserDto>> getUser(@PathVariable String userId) {
        return getResponseEntity(UserDto.from(userService.findActiveUser(userId)));
    }

    @Operation(summary = "사용자 정보 수정 (ADMIN 전용)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PutMapping("/{userId}")
    public ResponseEntity<CommonResponseDto<Void>> updateUser(
            @PathVariable String userId,
            @Valid @RequestBody UserUpsertDto dto,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        userService.updateUser(userId, dto);
        return getResponseEntity();
    }

    @Operation(summary = "사용자 비활성화 (ADMIN 전용, 논리 삭제)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{userId}")
    public ResponseEntity<CommonResponseDto<Void>> deactivateUser(
            @PathVariable String userId,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        userService.deactivateUser(userId);
        return getResponseEntity();
    }

    @Operation(summary = "사용자 물리 삭제 (ADMIN 전용)",
               description = "사용자 레코드를 DB에서 완전히 삭제한다. 연관 리프레시 토큰도 함께 삭제된다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @DeleteMapping("/{userId}/permanent")
    public ResponseEntity<CommonResponseDto<Void>> deleteUser(
            @PathVariable String userId,
            HttpServletRequest request
    ) {
        roleGuard.requireAdmin(request);
        userService.deleteUser(userId);
        return getResponseEntity();
    }
}
