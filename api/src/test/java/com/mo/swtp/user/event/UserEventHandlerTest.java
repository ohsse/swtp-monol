package com.mo.swtp.user.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.mo.swtp.auth.domain.RefreshToken;
import com.mo.swtp.auth.repository.RefreshTokenRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link UserEventHandler} 단위 테스트.
 *
 * <p>Spring 컨텍스트 없이 Mock 의존성으로 핸들러 동작을 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class UserEventHandlerTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    private UserEventHandler handler;

    @BeforeEach
    void setUp() {
        handler = new UserEventHandler(refreshTokenRepository);
    }

    @Test
    void 비활성화_이벤트_수신_시_리프레시_토큰을_폐기한다() {
        RefreshToken token = RefreshToken.create("user01", "hash", LocalDateTime.now().plusDays(7));
        given(refreshTokenRepository.findByUserId("user01")).willReturn(Optional.of(token));

        handler.handle(new UserDeactivatedEvent("user01"));

        assertThat(token.isRevoked()).isTrue();
        assertThat(token.getRevokeDtm()).isNotNull();
    }

    @Test
    void 비활성화_이벤트_수신_시_토큰이_없으면_아무것도_하지_않는다() {
        given(refreshTokenRepository.findByUserId("user02")).willReturn(Optional.empty());

        handler.handle(new UserDeactivatedEvent("user02"));

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    void 삭제_이벤트_수신_시_리프레시_토큰을_삭제한다() {
        handler.handle(new UserDeletedEvent("user03"));

        verify(refreshTokenRepository).deleteByUserId("user03");
    }
}
