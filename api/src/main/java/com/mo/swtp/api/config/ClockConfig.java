package com.mo.swtp.api.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 시각 의존 컴포넌트용 {@link Clock} 빈 설정.
 *
 * <p>서버 시점 기준 윈도우를 계산하는 서비스({@code MaxPeakStatusService} 등) 가 {@code LocalDate.now(clock)}
 * 으로 현재 시각을 얻도록 {@link Clock} 을 빈으로 분리한다. 운영은 시스템 기본 시간대 시계
 * ({@link Clock#systemDefaultZone()}) 를 사용하고, 단위 테스트는 생성자에 {@link Clock#fixed} 를 직접 주입해
 * 시점 의존을 제거한다 (사용량트렌드-3번섹션 PLAN1 §구현 방향 5).</p>
 */
@Configuration
public class ClockConfig {

    /**
     * 시스템 기본 시간대 기준 운영용 시계.
     *
     * @return {@link Clock#systemDefaultZone()}
     */
    @Bean
    public Clock clock() {
        return Clock.systemDefaultZone();
    }
}
