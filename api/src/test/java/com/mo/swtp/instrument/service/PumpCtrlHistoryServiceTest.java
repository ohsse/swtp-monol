package com.mo.swtp.instrument.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.domain.enumtype.ControlCommand;
import com.mo.swtp.instrument.domain.enumtype.ControlResult;
import com.mo.swtp.instrument.dto.PumpCtrlHistoryDto;
import com.mo.swtp.instrument.dto.PumpCtrlStatDto;
import com.mo.swtp.instrument.dto.PumpCtrlStatDto.ModeStat;
import com.mo.swtp.instrument.dto.PumpPeriodSearchDto;
import com.mo.swtp.instrument.exception.InstrumentErrorCode;
import com.mo.swtp.instrument.repository.PumpCtrlHistoryRepository;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * {@link PumpCtrlHistoryService} 단위 테스트.
 *
 * <p>제어이력 재도입 PLAN1 §성공 기준 — 섹션2(전체=모드합·비율 소수첫째·total=0 분모0 방어·3종 항상 포함·
 * 수동 NULL 제외) + 섹션3(기간 검증 예외·Repository 위임). 정렬·펌프명/제어태그 매핑은 Querydsl 책임이라
 * Repository 결과 통과만 검증한다.</p>
 */
@ExtendWith(MockitoExtension.class)
class PumpCtrlHistoryServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 6, 1, 8, 30, 0);

    @Mock
    private PumpCtrlHistoryRepository pumpCtrlHistoryRepository;

    @InjectMocks
    private PumpCtrlHistoryService pumpCtrlHistoryService;

    @Test
    void 섹션2_전체는_모드합이고_비율은_소수첫째자리로_산정된다() {
        // 이미지 분포 — AI 68 / AI추천 34 / AI분석 0 → 전체 102, 66.7%·33.3%·0.0%
        Map<AiDrvnModeCode, Long> counts = new EnumMap<>(AiDrvnModeCode.class);
        counts.put(AiDrvnModeCode.AI, 68L);
        counts.put(AiDrvnModeCode.AI_RECOMD, 34L);   // AI_ANLS 미포함 (0건)
        given(pumpCtrlHistoryRepository.countByAiDrvnMode(any(), any())).willReturn(counts);

        PumpCtrlStatDto result = pumpCtrlHistoryService.findCtrlStat(validSearch());

        assertThat(result.getTotalCount()).isEqualTo(102L);
        assertThat(result.getModeStats()).hasSize(3);
        assertThat(findMode(result, AiDrvnModeCode.AI).getCount()).isEqualTo(68L);
        assertThat(findMode(result, AiDrvnModeCode.AI).getRate()).isEqualByComparingTo("66.7");
        assertThat(findMode(result, AiDrvnModeCode.AI_RECOMD).getCount()).isEqualTo(34L);
        assertThat(findMode(result, AiDrvnModeCode.AI_RECOMD).getRate()).isEqualByComparingTo("33.3");
        assertThat(findMode(result, AiDrvnModeCode.AI_ANLS).getCount()).isZero();   // 0건도 포함
        assertThat(findMode(result, AiDrvnModeCode.AI_ANLS).getRate()).isEqualByComparingTo("0.0");

        BigDecimal rateSum = result.getModeStats().stream()
                .map(ModeStat::getRate)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(rateSum).isEqualByComparingTo("100.0");
    }

    @Test
    void 섹션2_전체가_0이면_분모0_방어로_모든_비율이_0이다() {
        given(pumpCtrlHistoryRepository.countByAiDrvnMode(any(), any()))
                .willReturn(new EnumMap<>(AiDrvnModeCode.class));

        PumpCtrlStatDto result = pumpCtrlHistoryService.findCtrlStat(validSearch());

        assertThat(result.getTotalCount()).isZero();
        assertThat(result.getModeStats()).hasSize(3);
        assertThat(result.getModeStats())
                .allSatisfy(m -> assertThat(m.getCount()).isZero());
        assertThat(result.getModeStats())
                .allSatisfy(m -> assertThat(m.getRate()).isEqualByComparingTo("0"));
    }

    @Test
    void 섹션2_응답은_AI_AI추천_AI분석_3종을_NULL_수동_없이_항상_포함한다() {
        Map<AiDrvnModeCode, Long> counts = new EnumMap<>(AiDrvnModeCode.class);
        counts.put(AiDrvnModeCode.AI, 5L);
        given(pumpCtrlHistoryRepository.countByAiDrvnMode(any(), any())).willReturn(counts);

        PumpCtrlStatDto result = pumpCtrlHistoryService.findCtrlStat(validSearch());

        assertThat(result.getModeStats())
                .extracting(ModeStat::getAiDrvnMod)
                .containsExactly(AiDrvnModeCode.AI, AiDrvnModeCode.AI_RECOMD, AiDrvnModeCode.AI_ANLS);
        assertThat(result.getModeStats())
                .noneSatisfy(m -> assertThat(m.getAiDrvnMod()).isNull());
    }

    @Test
    void 섹션2_기간이_유효하지_않으면_INVALID_INQ_PERIOD_예외가_발생한다() {
        PumpPeriodSearchDto invalid = new PumpPeriodSearchDto();   // fromDt/toDt null

        assertThatThrownBy(() -> pumpCtrlHistoryService.findCtrlStat(invalid))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_INQ_PERIOD);
        then(pumpCtrlHistoryRepository).shouldHaveNoInteractions();
    }

    @Test
    void 섹션3_정상_기간이면_Repository_결과를_그대로_반환한다() {
        List<PumpCtrlHistoryDto> rows = List.of(sampleRow());
        given(pumpCtrlHistoryRepository.findCtrlHistoryList(any(), any())).willReturn(rows);

        List<PumpCtrlHistoryDto> result = pumpCtrlHistoryService.findCtrlHistory(validSearch());

        assertThat(result).isSameAs(rows);
    }

    @Test
    void 섹션3_기간이_역전되면_INVALID_INQ_PERIOD_예외가_발생한다() {
        PumpPeriodSearchDto reversed = new PumpPeriodSearchDto();
        reversed.setFromDt(LocalDate.of(2026, 6, 30));
        reversed.setToDt(LocalDate.of(2026, 6, 1));

        assertThatThrownBy(() -> pumpCtrlHistoryService.findCtrlHistory(reversed))
                .isInstanceOf(RestApiException.class)
                .extracting(e -> ((RestApiException) e).getErrorCode())
                .isEqualTo(InstrumentErrorCode.INVALID_INQ_PERIOD);
        then(pumpCtrlHistoryRepository).shouldHaveNoInteractions();
    }

    // ────────────────────────────────────────────────────────────────────────
    // 헬퍼

    private PumpPeriodSearchDto validSearch() {
        PumpPeriodSearchDto search = new PumpPeriodSearchDto();
        search.setFromDt(LocalDate.of(2026, 6, 1));
        search.setToDt(LocalDate.of(2026, 6, 30));
        return search;
    }

    private ModeStat findMode(PumpCtrlStatDto dto, AiDrvnModeCode mode) {
        return dto.getModeStats().stream()
                .filter(m -> m.getAiDrvnMod() == mode)
                .findFirst()
                .orElseThrow();
    }

    private PumpCtrlHistoryDto sampleRow() {
        return new PumpCtrlHistoryDto(
                NOW, "I-PUMP-001", "송수1호기", "706-CMD-001-001",
                ControlCommand.START, ControlResult.COMPLETED, NOW, AiDrvnModeCode.AI);
    }
}
