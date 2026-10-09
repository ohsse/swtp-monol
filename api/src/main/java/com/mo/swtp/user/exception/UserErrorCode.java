package com.mo.swtp.user.exception;

import com.mo.swtp.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 사용자 도메인 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum UserErrorCode implements ErrorCode {

    USER_NOT_FOUND(404),
    DUPLICATE_USER_ID(409),
    INVALID_USER_PW(400),
    INVALID_CURRENT_PASSWORD(400),
    FORBIDDEN_ROLE(403);

    private final int httpStatus;
}
