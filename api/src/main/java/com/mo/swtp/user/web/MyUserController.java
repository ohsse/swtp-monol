package com.mo.swtp.user.web;

import com.mo.swtp.api.config.web.CommonController;
import com.mo.swtp.auth.exception.AuthErrorCode;
import com.mo.swtp.auth.web.JwtAuthenticationFilter;
import com.mo.swtp.common.enumtype.YnType;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.common.response.CommonResponseDto;
import com.mo.swtp.user.dto.MyUserPasswordUpsertDto;
import com.mo.swtp.user.dto.MyUserProfileUpsertDto;
import com.mo.swtp.user.dto.MyUserTtylUpsertDto;
import com.mo.swtp.user.dto.UserDto;
import com.mo.swtp.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 본인 사용자 정보 API 컨트롤러 (인증된 사용자 전용).
 *
 * <p>JWT subject 로 본인을 식별하므로 URL 에 사용자 ID 를 노출하지 않는다.
 * ADMIN 전용 {@link UserController} 와 권한·리소스 식별 모델이 다르므로 분리한다
 * ({@code MyMenuController} 선례 동일 패턴).</p>
 */
@Tag(name = "01-1. 내 사용자 정보")
@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class MyUserController extends CommonController {

    private final UserService userService;

    @Operation(summary = "본인 정보 조회",
               description = "JWT 토큰의 subject 에 해당하는 활성 사용자 정보를 반환한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "사용자 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @GetMapping
    public ResponseEntity<CommonResponseDto<UserDto>> getMyInfo(HttpServletRequest request) {
        String subject = extractSubject(request);
        return getResponseEntity(UserDto.from(userService.findActiveUser(subject)));
    }

    @Operation(summary = "본인 이름 변경",
               description = "인증된 사용자가 자신의 이름을 변경한다. 권한(userRole) 은 변경되지 않는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "사용자 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/profile")
    public ResponseEntity<CommonResponseDto<Void>> changeMyProfile(
            @Valid @RequestBody MyUserProfileUpsertDto dto,
            HttpServletRequest request
    ) {
        String subject = extractSubject(request);
        userService.changeMyProfile(subject, dto.getUserNm());
        return getResponseEntity();
    }

    @Operation(summary = "본인 비밀번호 변경",
               description = "현재 비밀번호 검증 후 새 비밀번호로 변경한다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "400", description = "현재 비밀번호 불일치 또는 잘못된 요청"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "404", description = "사용자 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/password")
    public ResponseEntity<CommonResponseDto<Void>> changeMyPassword(
            @Valid @RequestBody MyUserPasswordUpsertDto dto,
            HttpServletRequest request
    ) {
        String subject = extractSubject(request);
        userService.changeMyPassword(subject, dto.getCurrentPw(), dto.getNewPw());
        return getResponseEntity();
    }

    @Operation(summary = "사용자 튜토리얼 플래그 변경")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "성공"),
            @ApiResponse(responseCode = "401", description = "인증 실패"),
            @ApiResponse(responseCode = "403", description = "권한 없음"),
            @ApiResponse(responseCode = "404", description = "사용자 없음"),
            @ApiResponse(responseCode = "500", description = "서버 오류")
    })
    @PatchMapping("/{userId}/tutorial")
    public ResponseEntity<CommonResponseDto<Void>> changeTutorial(@PathVariable String userId, @RequestBody MyUserTtylUpsertDto flagDto, HttpServletRequest request) {
        String subject = extractSubject(request);
        if (!subject.equals(userId)) {
            throw new RestApiException(AuthErrorCode.FORBIDDEN);
        }
        userService.changeTtrylUseYn(userId, flagDto.getTtrylUseYn());
        return getResponseEntity();
        
    }
    /**
     * 요청 attribute 에서 인증된 사용자의 subject(userId) 를 추출한다.
     *
     * <p>{@link JwtAuthenticationFilter} 가 검증 성공 시 적재한 attribute 를 읽는다.
     * 누락 시 인증 컨텍스트 부재로 간주하여 UNAUTHORIZED 예외를 던진다.</p>
     */
    private String extractSubject(HttpServletRequest request) {
        String subject = (String) request.getAttribute(JwtAuthenticationFilter.AUTH_SUBJECT_ATTRIBUTE);
        if (subject == null) {
            throw new RestApiException(AuthErrorCode.UNAUTHORIZED);
        }
        return subject;
    }
}
