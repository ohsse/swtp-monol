package com.mo.swtp.proc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;

import com.mo.swtp.auth.web.ApiErrorResponseWriter;
import com.mo.swtp.proc.domain.AiDrvnMode;
import com.mo.swtp.proc.domain.AiDrvnModeHistory;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import com.mo.swtp.proc.event.AiDrvnModeChangedEvent;
import com.mo.swtp.proc.repository.AiDrvnModeHistoryRepository;
import com.mo.swtp.proc.repository.AiDrvnModeRepository;
import com.mo.swtp.proc.sse.AiDrvnModeSseService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.test.annotation.Commit;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * {@link AiDrvnModeService} 통합 테스트 — 6 단계 트랜잭션 검증.
 *
 * <p>시드 데이터 'PUMP_CONTROL' 의존 (V9_4 마이그레이션).</p>
 */
@SpringBootTest(webEnvironment = WebEnvironment.NONE)
@ActiveProfiles("test")
class AiDrvnModeServiceIntegrationTest {

    @MockitoBean
    private ApiErrorResponseWriter apiErrorResponseWriter;

    /** SSE 보관소를 mock 으로 격리 — 트랜잭션 commit 후 broadcast 호출 verify */
    @MockitoBean
    private AiDrvnModeSseService sseService;

    @Autowired
    private AiDrvnModeService aiDrvnModeService;

    @Autowired
    private AiDrvnModeRepository aiDrvnModeRepository;

    @Autowired
    private AiDrvnModeHistoryRepository aiDrvnModeHistoryRepository;

    @Test
    @Commit
    void 변경_트랜잭션은_세_작업이_원자적으로_적용되며_이벤트가_발행된다() {
        // given: 시드 'PUMP_CONTROL' 존재. 사전 정리 — 기존 이력·마스터 행 제거
        aiDrvnModeRepository.deleteById("PUMP_CONTROL");
        aiDrvnModeHistoryRepository.findActiveByProcId("PUMP_CONTROL")
                .ifPresent(aiDrvnModeHistoryRepository::delete);

        // when: 최초 모드 설정 — AI
        aiDrvnModeService.changeAiDrvnMode("PUMP_CONTROL", AiDrvnModeCode.AI);

        // then: 마스터 행 + 활성 이력 행 1건 INSERT
        AiDrvnMode master = aiDrvnModeRepository.findById("PUMP_CONTROL").orElseThrow();
        assertThat(master.getAiDrvnModCd()).isEqualTo(AiDrvnModeCode.AI);

        AiDrvnModeHistory active = aiDrvnModeHistoryRepository.findActiveByProcId("PUMP_CONTROL").orElseThrow();
        assertThat(active.getAiDrvnModCd()).isEqualTo(AiDrvnModeCode.AI);
        assertThat(active.getEndDtm()).isNull();

        // when: 모드 변경 — AI_RECOMD
        aiDrvnModeService.changeAiDrvnMode("PUMP_CONTROL", AiDrvnModeCode.AI_RECOMD);

        // then: 마스터 UPDATE + 직전 활성 행 end_dtm 갱신 + 신규 활성 행 INSERT
        AiDrvnMode updated = aiDrvnModeRepository.findById("PUMP_CONTROL").orElseThrow();
        assertThat(updated.getAiDrvnModCd()).isEqualTo(AiDrvnModeCode.AI_RECOMD);

        AiDrvnModeHistory newActive = aiDrvnModeHistoryRepository.findActiveByProcId("PUMP_CONTROL").orElseThrow();
        assertThat(newActive.getAiDrvnModCd()).isEqualTo(AiDrvnModeCode.AI_RECOMD);
        assertThat(newActive.getEndDtm()).isNull();

        // AFTER_COMMIT 리스너가 sseService.broadcast 호출 최소 2회 검증
        then(sseService).should(atLeastOnce()).broadcast(any(AiDrvnModeChangedEvent.class));

        // cleanup
        aiDrvnModeRepository.deleteById("PUMP_CONTROL");
        aiDrvnModeHistoryRepository.findByProcIdOrderByStartDtmDesc("PUMP_CONTROL",
                org.springframework.data.domain.Pageable.unpaged())
                .forEach(aiDrvnModeHistoryRepository::delete);
    }
}
