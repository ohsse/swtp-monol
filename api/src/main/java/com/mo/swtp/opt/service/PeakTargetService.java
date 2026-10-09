package com.mo.swtp.opt.service;

import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.opt.domain.PeakTarget;
import com.mo.swtp.opt.dto.PeakTargetDto;
import com.mo.swtp.opt.dto.PeakTargetUpsertDto;
import com.mo.swtp.opt.event.PeakTargetEventPublisher;
import com.mo.swtp.opt.exception.OptErrorCode;
import com.mo.swtp.opt.repository.PeakTargetRepository;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 전력피크 목표값 조회·저장 서비스.
 *
 * <p>시스템 전역 단일 행 ({@link PeakTarget#PEAK_TARGET_CD}) 을 조회·갱신한다. 시드 1행이 DDL 로
 * 항상 존재하므로 저장은 항상 UPDATE — proc 도메인의 6단계 트랜잭션 중 INSERT·이력·동시성 충돌
 * 분기가 불필요하다 (전력피크분석-1번섹션 PLAN1 §proc 대비 단순화). 저장 흐름 3단계:</p>
 * <ol>
 *   <li>{@code findByPeakCdForUpdate} — SELECT FOR UPDATE 락 (시드 보장 → 항상 존재)</li>
 *   <li>{@link PeakTarget#change} — registerEvent 축적 + JPA dirty checking UPDATE</li>
 *   <li>{@link PeakTargetEventPublisher#changeAndPublish} — publishAndClear →
 *       commit → AFTER_COMMIT Listener → SSE 전파</li>
 * </ol>
 *
 * <p>시드 부재 (배포 누락·픽스처 결손) 는 {@link OptErrorCode#PEAK_TARGET_NOT_INITIALIZED} (500)
 * 으로 표면화한다 — silent self-heal (create) 대신 배포 오류를 명시적으로 신호한다 (PLAN1 §단순성).</p>
 *
 * <p>{@code DomainEventEntity} + 전용 Publisher 패턴 정합 — Service 에 {@code ApplicationEventPublisher}
 * 직접 주입 금지. 선례: {@code AiDrvnModeService}.</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PeakTargetService {

    private final PeakTargetRepository peakTargetRepository;
    private final PeakTargetEventPublisher peakTargetEventPublisher;

    /**
     * 현재 목표 피크치를 조회한다.
     *
     * @return 현재 목표 피크치 DTO
     * @throws RestApiException {@link OptErrorCode#PEAK_TARGET_NOT_INITIALIZED} (시드 부재)
     */
    public PeakTargetDto getPeakTarget() {
        return peakTargetRepository.findById(PeakTarget.PEAK_TARGET_CD)
                .map(PeakTargetDto::from)
                .orElseThrow(() -> new RestApiException(OptErrorCode.PEAK_TARGET_NOT_INITIALIZED));
    }

    /**
     * 목표 피크치를 변경·저장한다 (저장 흐름 3단계).
     *
     * <p>저장 메서드만 {@code @Transactional} (기본 READ_COMMITTED) 로 오버라이드하며,
     * {@code findByPeakCdForUpdate} 의 {@code PESSIMISTIC_WRITE} 락은 입력값 반영·이벤트 발행
     * 직후 커밋되어 락 보유 시간이 최소화된다 (PLAN1 부록 권고 2).</p>
     *
     * @param dto 저장 요청 (목표 피크 전력값, {@code @Positive} 검증 완료)
     * @return 변경 후 목표 피크치 DTO
     * @throws RestApiException {@link OptErrorCode#PEAK_TARGET_NOT_INITIALIZED} (시드 부재)
     */
    @Transactional
    public PeakTargetDto changePeakTarget(PeakTargetUpsertDto dto) {
        PeakTarget peakTarget = peakTargetRepository.findByPeakCdForUpdate(PeakTarget.PEAK_TARGET_CD)
                .orElseThrow(() -> new RestApiException(OptErrorCode.PEAK_TARGET_NOT_INITIALIZED));

        peakTarget.change(dto.getTargetPeakElpwr(), LocalDateTime.now());
        peakTargetEventPublisher.changeAndPublish(peakTarget);

        log.debug("전력피크 목표값 변경 — targetPeakElpwr={}", dto.getTargetPeakElpwr());
        return PeakTargetDto.from(peakTarget);
    }
}
