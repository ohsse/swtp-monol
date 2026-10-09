package com.mo.swtp.proc.service;

import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.proc.domain.AiDrvnMode;
import com.mo.swtp.proc.domain.AiDrvnModeHistory;
import com.mo.swtp.proc.domain.Process;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import com.mo.swtp.proc.dto.AiDrvnModeDto;
import com.mo.swtp.proc.dto.AiDrvnModeHistoryDto;
import com.mo.swtp.proc.event.AiDrvnModeEventPublisher;
import com.mo.swtp.proc.exception.ProcErrorCode;
import com.mo.swtp.proc.repository.AiDrvnModeHistoryRepository;
import com.mo.swtp.proc.repository.AiDrvnModeRepository;
import com.mo.swtp.proc.repository.ProcessRepository;
import java.time.LocalDateTime;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * AI 운전모드 변경 서비스.
 *
 * <p>변경 트랜잭션은 6단계로 진행한다 (송수펌프제어분석-2번섹션 PLAN3 §변경 트랜잭션 흐름):</p>
 * <ol>
 *   <li>{@code AiDrvnModeRepository#findByProcIdForUpdate} — SELECT FOR UPDATE 락</li>
 *   <li>직전 활성 이력 행의 {@code end_dtm} UPDATE</li>
 *   <li>마스터 행 ({@code ai_drvn_mod_p}) UPSERT (최초면 INSERT, 이후 UPDATE) —
 *       {@code change}/{@code create} 내부에서 {@code registerEvent()} 로 이벤트 축적</li>
 *   <li>신규 이력 행 ({@code ai_drvn_mod_h}) INSERT</li>
 *   <li>Publisher 위임 — {@link AiDrvnModeEventPublisher} 가 엔티티의 축적된
 *       도메인 이벤트를 {@code publishAndClear} 로 발행</li>
 *   <li>트랜잭션 commit → AFTER_COMMIT Listener 실행 → SSE 전파</li>
 * </ol>
 *
 * <p>동시성 안전망: 부분 UNIQUE INDEX
 * ({@code uk_ai_drvn_mod_h_proc_active (proc_id) WHERE end_dtm IS NULL}) +
 * SELECT FOR UPDATE 락 이중 방어. 충돌 시 {@link DataIntegrityViolationException} 을
 * {@link ProcErrorCode#AI_MODE_CONCURRENT_UPDATE} (409) 로 매핑한다.</p>
 *
 * <p>{@code DomainEventEntity} 패턴 정합 (PLAN3, 2026-05-20) — {@link AiDrvnModeEventPublisher}
 * 컴포넌트 위임으로 엔티티의 자기 책임성 (자기 상태 변경 이벤트 표현) 을 보존한다.
 * 선례: {@code UserEventPublisher}. REVIEW1 블로커 해소.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AiDrvnModeService {

    private final ProcessRepository processRepository;
    private final AiDrvnModeRepository aiDrvnModeRepository;
    private final AiDrvnModeHistoryRepository aiDrvnModeHistoryRepository;
    private final AiDrvnModeEventPublisher aiDrvnModeEventPublisher;

    /**
     * 공정/제어대상의 현재 AI 운전모드를 조회한다.
     *
     * @return 현재 모드 (최초 설정 전이면 Optional.empty)
     */
    public Optional<AiDrvnModeDto> findCurrent(String procId) {
        return aiDrvnModeRepository.findById(procId).map(AiDrvnModeDto::from);
    }

    /**
     * 공정/제어대상별 변경 이력 페이지네이션 조회 (최신순).
     */
    public Page<AiDrvnModeHistoryDto> findHistory(String procId, Pageable pageable) {
        return aiDrvnModeHistoryRepository
                .findByProcIdOrderByStartDtmDesc(procId, pageable)
                .map(AiDrvnModeHistoryDto::from);
    }

    /**
     * AI 운전모드 변경 — 6 단계 트랜잭션.
     *
     * @param procId      공정/제어대상 ID
     * @param newModeCd   새 모드 코드
     * @return 변경 후 현재 모드 DTO
     * @throws RestApiException {@link ProcErrorCode#PROC_NOT_FOUND} ·
     *                          {@link ProcErrorCode#AI_MODE_CONCURRENT_UPDATE} (동시 변경 충돌)
     */
    @Transactional
    public AiDrvnModeDto changeAiDrvnMode(String procId, AiDrvnModeCode newModeCd) {
        Process process = processRepository.findById(procId)
                .orElseThrow(() -> new RestApiException(ProcErrorCode.PROC_NOT_FOUND));

        LocalDateTime now = LocalDateTime.now();
        try {
            // 1단계: 마스터 SELECT FOR UPDATE 락 (최초 설정 전이면 Optional.empty)
            Optional<AiDrvnMode> currentOpt = aiDrvnModeRepository.findByProcIdForUpdate(procId);

            // 2단계: 직전 활성 이력 행 end_dtm UPDATE (최초 설정이 아닐 때만)
            // 명시 flush 의무 — JPA action queue 가 INSERT 를 UPDATE 보다 먼저 처리하면
            // 부분 UNIQUE INDEX (proc_id) WHERE end_dtm IS NULL 가 두 활성 행으로 충돌한다
            aiDrvnModeHistoryRepository.findActiveByProcId(procId)
                    .ifPresent(active -> {
                        active.close(now);
                        aiDrvnModeHistoryRepository.flush();
                    });

            // 3단계: 마스터 UPSERT — change()/create() 가 registerEvent() 로 이벤트 축적
            boolean isUpdate = currentOpt.isPresent();
            AiDrvnMode mode = currentOpt
                    .map(existing -> {
                        existing.change(newModeCd, now);
                        return existing;
                    })
                    .orElseGet(() -> AiDrvnMode.create(process, newModeCd, now));

            // 4단계: 신규 이력 INSERT
            aiDrvnModeHistoryRepository.save(
                    AiDrvnModeHistory.create(procId, newModeCd, now)
            );

            // flush 강제 — UNIQUE 위반을 5단계 publish 이전에 트랩
            aiDrvnModeHistoryRepository.flush();

            // 5단계: Publisher 위임 — 엔티티의 축적된 도메인 이벤트를 발행 + 클리어
            //   - UPDATE 경로: dirty checking 으로 flush, publishAndClear
            //   - INSERT 경로: save + publishAndClear
            if (isUpdate) {
                aiDrvnModeEventPublisher.changeAndPublish(mode);
            } else {
                mode = aiDrvnModeEventPublisher.createAndPublish(mode);
            }

            // 6단계: 트랜잭션 commit (메서드 종료 시점) → AFTER_COMMIT Listener → SSE 전파
            return AiDrvnModeDto.from(mode);

        } catch (DataIntegrityViolationException e) {
            log.warn("AI 운전모드 동시 변경 충돌 — procId={}, mode={}", procId, newModeCd, e);
            throw new RestApiException(ProcErrorCode.AI_MODE_CONCURRENT_UPDATE, e);
        }
    }
}
