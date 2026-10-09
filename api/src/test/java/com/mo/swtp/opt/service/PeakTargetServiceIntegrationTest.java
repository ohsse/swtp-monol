package com.mo.swtp.opt.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mo.swtp.auth.web.ApiErrorResponseWriter;
import com.mo.swtp.opt.dto.PeakTargetDto;
import com.mo.swtp.opt.dto.PeakTargetUpsertDto;
import com.mo.swtp.opt.sse.PeakTargetSseService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * {@link PeakTargetService} 통합 테스트 — 저장→조회 왕복 일관성.
 *
 * <p>시드 1행 'PEAK_TARGET' 의존 (V5_1__opt_patch.sql). 클래스 레벨 {@link Transactional} 롤백으로
 * 전역 단일 행을 영구 변경하지 않는다. AFTER_COMMIT 미발화 → SSE broadcast 미호출 (mock 격리).</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
@Transactional
class PeakTargetServiceIntegrationTest {

    @MockitoBean
    private ApiErrorResponseWriter apiErrorResponseWriter;

    /** SSE 보관소를 mock 으로 격리 — 롤백 트랜잭션이라 broadcast 미호출 */
    @MockitoBean
    private PeakTargetSseService sseService;

    @Autowired
    private PeakTargetService peakTargetService;

    @Test
    void 목표값_저장_후_조회_시_변경값을_반환한다() {
        // given: 시드 'PEAK_TARGET' 존재 (초기 0)
        PeakTargetUpsertDto dto = new PeakTargetUpsertDto();
        dto.setTargetPeakElpwr(new BigDecimal("1234.5678"));

        // when: 저장
        PeakTargetDto saved = peakTargetService.changePeakTarget(dto);

        // then: 저장 응답 + 재조회 모두 변경값 반환
        assertThat(saved.getTargetPeakElpwr()).isEqualByComparingTo(new BigDecimal("1234.5678"));

        PeakTargetDto fetched = peakTargetService.getPeakTarget();
        assertThat(fetched.getTargetPeakElpwr()).isEqualByComparingTo(new BigDecimal("1234.5678"));
    }
}
