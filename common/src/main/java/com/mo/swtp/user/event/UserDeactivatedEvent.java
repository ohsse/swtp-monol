package com.mo.swtp.user.event;

/**
 * 사용자 논리 삭제(비활성화) 이벤트.
 *
 * <p>핸들러({@code UserEventHandler})가 {@code BEFORE_COMMIT} 시점에
 * 해당 사용자의 리프레시 토큰을 폐기(revoke)한다.</p>
 *
 * @param userId 비활성화된 사용자 ID
 */
public record UserDeactivatedEvent(String userId) {
}
