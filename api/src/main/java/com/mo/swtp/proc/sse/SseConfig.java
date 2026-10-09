package com.mo.swtp.proc.sse;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SSE 도메인 인프라 설정.
 *
 * <p>{@link AiDrvnModeSseService#sendPing()} 의 {@code @Scheduled} 가 작동하기 위해
 * {@link EnableScheduling} 을 활성화한다. 향후 다른 도메인에서 {@code @Scheduled} 사용 시 본 설정이
 * 영향을 미친다 — 도메인별 옵트인을 원하면 별도 메커니즘 필요.</p>
 */
@Configuration
@EnableScheduling
public class SseConfig {
}
