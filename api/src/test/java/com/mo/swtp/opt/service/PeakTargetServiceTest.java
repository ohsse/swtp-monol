package com.mo.swtp.opt.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;

import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.opt.domain.PeakTarget;
import com.mo.swtp.opt.dto.PeakTargetUpsertDto;
import com.mo.swtp.opt.event.PeakTargetEventPublisher;
import com.mo.swtp.opt.exception.OptErrorCode;
import com.mo.swtp.opt.repository.PeakTargetRepository;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PeakTargetService} 단위 테스트 — Mockito 격리.
 *
 * <p>핵심 검증: 저장 시 도메인 이벤트 발행 1회 / 시드 부재 시 명시적 오류.</p>
 */
@ExtendWith(MockitoExtension.class)
class PeakTargetServiceTest {

    @Mock
    private PeakTargetRepository peakTargetRepository;

    @Mock
    private PeakTargetEventPublisher peakTargetEventPublisher;

    @InjectMocks
    private PeakTargetService peakTargetService;

    @Test
    void 목표값_저장_시_change와_changeAndPublish가_각_1회_호출된다() {
        PeakTarget peakTarget = mock(PeakTarget.class);
        given(peakTargetRepository.findByPeakCdForUpdate(PeakTarget.PEAK_TARGET_CD))
                .willReturn(Optional.of(peakTarget));
        BigDecimal value = new BigDecimal("900.0000");
        PeakTargetUpsertDto dto = new PeakTargetUpsertDto();
        dto.setTargetPeakElpwr(value);

        peakTargetService.changePeakTarget(dto);

        then(peakTarget).should(times(1)).change(eq(value), any(LocalDateTime.class));
        then(peakTargetEventPublisher).should(times(1)).changeAndPublish(peakTarget);
    }

    @Test
    void 저장_시_시드_부재면_PEAK_TARGET_NOT_INITIALIZED_예외가_발생한다() {
        given(peakTargetRepository.findByPeakCdForUpdate(PeakTarget.PEAK_TARGET_CD))
                .willReturn(Optional.empty());
        PeakTargetUpsertDto dto = new PeakTargetUpsertDto();
        dto.setTargetPeakElpwr(new BigDecimal("900"));

        assertThatThrownBy(() -> peakTargetService.changePeakTarget(dto))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(OptErrorCode.PEAK_TARGET_NOT_INITIALIZED);

        then(peakTargetEventPublisher).shouldHaveNoInteractions();
    }

    @Test
    void 조회_시_시드_부재면_PEAK_TARGET_NOT_INITIALIZED_예외가_발생한다() {
        given(peakTargetRepository.findById(PeakTarget.PEAK_TARGET_CD))
                .willReturn(Optional.empty());

        assertThatThrownBy(() -> peakTargetService.getPeakTarget())
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(OptErrorCode.PEAK_TARGET_NOT_INITIALIZED);
    }
}
