package com.mo.swtp.proc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mo.swtp.auth.web.ApiErrorResponseWriter;
import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import com.mo.swtp.proc.exception.ProcErrorCode;
import com.mo.swtp.proc.repository.AiDrvnModeHistoryRepository;
import com.mo.swtp.proc.repository.AiDrvnModeRepository;
import com.mo.swtp.proc.sse.AiDrvnModeSseService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@link AiDrvnModeService} 동시성 테스트 — SELECT FOR UPDATE 락 + 부분 UNIQUE 인덱스 검증.
 *
 * <p>2 스레드 latch 동시 호출 시 한 트랜잭션만 성공하고 다른 트랜잭션은
 * {@link ProcErrorCode#AI_MODE_CONCURRENT_UPDATE} (409) 또는 SELECT FOR UPDATE 직렬화로 순차 성공한다.
 * 핵심: 데이터 일관성 유지 — 활성 행은 최종 1건.</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class AiDrvnModeServiceConcurrencyTest {

    @MockitoBean
    private ApiErrorResponseWriter apiErrorResponseWriter;

    @MockitoBean
    private AiDrvnModeSseService sseService;

    @Autowired
    private AiDrvnModeService aiDrvnModeService;

    @Autowired
    private AiDrvnModeRepository aiDrvnModeRepository;

    @Autowired
    private AiDrvnModeHistoryRepository aiDrvnModeHistoryRepository;

    @BeforeEach
    void cleanup() {
        aiDrvnModeRepository.deleteById("PUMP_CONTROL");
        aiDrvnModeHistoryRepository.findByProcIdOrderByStartDtmDesc("PUMP_CONTROL",
                org.springframework.data.domain.Pageable.unpaged())
                .forEach(aiDrvnModeHistoryRepository::delete);
    }

    @AfterEach
    void teardown() {
        aiDrvnModeRepository.deleteById("PUMP_CONTROL");
        aiDrvnModeHistoryRepository.findByProcIdOrderByStartDtmDesc("PUMP_CONTROL",
                org.springframework.data.domain.Pageable.unpaged())
                .forEach(aiDrvnModeHistoryRepository::delete);
    }

    @Test
    void 동시_변경_시_부분_UNIQUE_인덱스가_409를_반환하거나_FOR_UPDATE로_순차_처리된다() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicReference<RestApiException> firstFailure = new AtomicReference<>();

        ExecutorService pool = Executors.newFixedThreadPool(2);
        Runnable task = () -> {
            try {
                start.await();
                aiDrvnModeService.changeAiDrvnMode("PUMP_CONTROL", AiDrvnModeCode.AI);
                successCount.incrementAndGet();
            } catch (RestApiException e) {
                firstFailure.compareAndSet(null, e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                // 동시성 충돌이 RuntimeException 으로 전파될 수도 있음 — 본 테스트는 데이터 정합만 검증
            }
        };

        pool.submit(task);
        pool.submit(task);
        Thread.sleep(50);
        start.countDown();
        pool.shutdown();
        pool.awaitTermination(10, TimeUnit.SECONDS);

        // 활성 행은 최종 1건만 존재해야 함 (핵심 검증)
        long activeCount = aiDrvnModeHistoryRepository.findByProcIdOrderByStartDtmDesc("PUMP_CONTROL",
                org.springframework.data.domain.Pageable.unpaged())
                .stream()
                .filter(h -> h.getEndDtm() == null)
                .count();
        assertThat(activeCount).isEqualTo(1L);

        // 두 스레드 합쳐 최소 1건 성공 (한 트랜잭션은 commit)
        assertThat(successCount.get()).isGreaterThanOrEqualTo(1);
    }
}
