package com.mo.swtp.user.event;

/**
 * 사용자 물리 삭제 이벤트.
 *
 * <p>핸들러({@code UserEventHandler})가 {@code BEFORE_COMMIT} 시점에
 * FK 제약 위반을 방지하기 위해 해당 사용자의 리프레시 토큰을 먼저 삭제한다.</p>
 *
 * @param userId 삭제된 사용자 ID
 */
public record UserDeletedEvent(String userId) {
}
