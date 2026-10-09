package com.mo.swtp.auth.exception;

import com.mo.swtp.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 인증 도메인 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum AuthErrorCode implements ErrorCode {

    LOGIN_FAILED(401),
    /**
     * 인증 컨텍스트가 없는 상황. JWT 필터가 적재한
     * {@code request.getAttribute(AUTH_SUBJECT_ATTRIBUTE)} 가 NULL 인 경우 등에 사용된다.
     * 정상 흐름에서는 JWT 필터가 항상 채우므로 방어 코드 용도.
     */
    UNAUTHORIZED(401),
    FORBIDDEN(403);

    private final int httpStatus;
}
