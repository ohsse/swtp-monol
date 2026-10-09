package com.mo.swtp.user.event;

import com.mo.swtp.common.event.AbstractDomainEventPublisher;
import com.mo.swtp.user.domain.User;
import com.mo.swtp.user.repository.UserRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 사용자 도메인 이벤트 발행기.
 *
 * <p>{@link AbstractDomainEventPublisher}를 상속하여 사용자 비활성화·물리 삭제 시
 * 도메인 이벤트를 발행한다.</p>
 */
@Component
public class UserEventPublisher extends AbstractDomainEventPublisher<User> {

    /**
     * 생성자.
     *
     * @param repository 사용자 리포지토리
     * @param publisher  Spring 이벤트 발행기
     */
    public UserEventPublisher(UserRepository repository, ApplicationEventPublisher publisher) {
        super(repository, publisher);
    }

    /**
     * 사용자 비활성화 이벤트를 발행한다.
     *
     * <p>엔티티 상태는 JPA dirty checking으로 flush되므로 별도 {@code save()} 호출이 불필요하다.</p>
     *
     * @param user 비활성화된 사용자 엔티티
     */
    public void deactivateAndPublish(User user) {
        super.publishAndClear(user, new UserDeactivatedEvent(user.getUserId()));
    }

    /**
     * 사용자 물리 삭제 이벤트를 발행한다.
     *
     * <p>이벤트 등록 후 {@code repository.delete(user)}를 수행하고, {@code BEFORE_COMMIT}
     * 핸들러가 연관 RefreshToken을 먼저 삭제하여 FK 제약 위반을 방지한다.</p>
     *
     * @param user 삭제할 사용자 엔티티
     */
    public void deleteAndPublish(User user) {
        super.deleteAndPublish(user, new UserDeletedEvent(user.getUserId()));
    }
}
