package com.mo.swtp.menu.exception;

import com.mo.swtp.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 메뉴 도메인 에러 코드.
 */
@Getter
@RequiredArgsConstructor
public enum MenuErrorCode implements ErrorCode {

    MENU_NOT_FOUND(404),
    DUPLICATE_MENU_NM(409),
    MENU_DEPTH_EXCEEDED(400),
    MENU_HAS_ACTIVE_CHILDREN(409),
    INVALID_PARENT_MENU(400);

    private final int httpStatus;
}
