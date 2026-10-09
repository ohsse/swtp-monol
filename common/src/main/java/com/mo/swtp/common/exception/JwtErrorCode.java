package com.mo.swtp.common.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum JwtErrorCode implements ErrorCode {

    MISSING_ACCESS_TOKEN(401),
    INVALID_TOKEN(401),
    EXPIRED_TOKEN(401),
    REFRESH_TOKEN_NOT_FOUND(401),
    REFRESH_TOKEN_MISMATCH(401),
    REFRESH_TOKEN_REVOKED(401);

    private final int httpStatus;
}
