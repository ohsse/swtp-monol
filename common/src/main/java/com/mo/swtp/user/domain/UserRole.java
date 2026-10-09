package com.mo.swtp.user.domain;

/**
 * 사용자 권한 역할 코드.
 *
 * <ul>
 *   <li>ADMIN: 사용자 관리, 기기/모드 제어 등 전체 권한</li>
 *   <li>USER: 조회 전용 권한</li>
 * </ul>
 */
public enum UserRole {
    ADMIN,
    USER
}
