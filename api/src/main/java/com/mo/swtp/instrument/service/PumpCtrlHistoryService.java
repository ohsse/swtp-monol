package com.mo.swtp.instrument.service;

import com.mo.swtp.common.exception.RestApiException;
import com.mo.swtp.instrument.dto.PumpCtrlHistoryDto;
import com.mo.swtp.instrument.dto.PumpCtrlStatDto;
import com.mo.swtp.instrument.dto.PumpCtrlStatDto.ModeStat;
import com.mo.swtp.instrument.dto.PumpPeriodSearchDto;
import com.mo.swtp.instrument.exception.InstrumentErrorCode;
import com.mo.swtp.instrument.repository.PumpCtrlHistoryRepository;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 송수펌프 제어이력 2·3번 섹션 — AI 운영 현황 통계 + 제어 이력 목록 조회 서비스.
 *
 * <p>동일한 제어이력 테이블({@code pump_ctrl_h})을 동일 조회기간(from~to)으로 조회하는 두 화면 섹션을
 * 한 서비스에 묶는다 — 2번섹션({@link #findCtrlStat})은 운전모드별 카운트·비율 통계, 3번섹션
 * ({@link #findCtrlHistory})은 시간 역순 목록 (제어이력 재도입 PLAN1 §구현 방향 Phase 3·4).</p>
 *
 * <p>조회 전용 — 제어 명령 발행(OT 아웃바운드)은 본 사이클 범위 밖 ({@code ot-integration.md §2·§5} 보류).
 * 기간 변환·검증은 {@link PumpPeriodSearchDto} SSOT 에 위임하며, 비율 산정·3종 모드 보강만 본 서비스가
 * 담당한다 (추상화 깊이 3단 이하 + 메서드 50줄 이내, {@code .claude/rules/coding-discipline.md §2.1}).</p>
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PumpCtrlHistoryService {

    /** 비율(%) 산정 분자 배율. */
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /** 비율 소수 자릿수 — 소수 첫째 자리 (이미지 66.7%·33.3% 정합). */
    private static final int RATE_SCALE = 1;

    private final PumpCtrlHistoryRepository pumpCtrlHistoryRepository;

    /**
     * 조회기간 동안의 AI 운영 현황 통계를 조회한다 (2번섹션).
     *
     * <p>운전모드별 카운트를 집계하고 비율(모드 카운트 ÷ 전체 카운트, %)을 산정한다. 수동 제어
     * ({@code ai_drvn_mod IS NULL})는 집계에서 제외되며, AI/AI_RECOMD/AI_ANLS 3종은 카운트 0건이어도
     * 항상 응답에 포함한다.</p>
     *
     * @param search from~to 조회기간 검색 조건
     * @return 전체 카운트 + 운전모드별 (카운트, 비율) 통계
     * @throws RestApiException {@link InstrumentErrorCode#INVALID_INQ_PERIOD} — 기간 결측 또는 from &gt; to
     */
    public PumpCtrlStatDto findCtrlStat(PumpPeriodSearchDto search) {
        if (!search.isValid()) {
            throw new RestApiException(InstrumentErrorCode.INVALID_INQ_PERIOD);
        }
        Map<AiDrvnModeCode, Long> counts = pumpCtrlHistoryRepository
                .countByAiDrvnMode(search.toStartDtm(), search.toEndExclusiveDtm());
        long total = counts.values().stream().mapToLong(Long::longValue).sum();

        List<ModeStat> modeStats = Arrays.stream(AiDrvnModeCode.values())
                .map(mode -> {
                    long count = counts.getOrDefault(mode, 0L);
                    return ModeStat.of(mode, count, computeRate(count, total));
                })
                .toList();
        return PumpCtrlStatDto.of(total, modeStats);
    }

    /**
     * 조회기간 동안의 제어 이력 목록을 시간 역순으로 조회한다 (3번섹션).
     *
     * @param search from~to 조회기간 검색 조건
     * @return 제어 이력 행 목록 ({@code ctrl_dtm} 내림차순). 이력 0건 시 빈 목록
     * @throws RestApiException {@link InstrumentErrorCode#INVALID_INQ_PERIOD} — 기간 결측 또는 from &gt; to
     */
    public List<PumpCtrlHistoryDto> findCtrlHistory(PumpPeriodSearchDto search) {
        if (!search.isValid()) {
            throw new RestApiException(InstrumentErrorCode.INVALID_INQ_PERIOD);
        }
        return pumpCtrlHistoryRepository
                .findCtrlHistoryList(search.toStartDtm(), search.toEndExclusiveDtm());
    }

    /**
     * 모드 비율(%)을 산정한다 — 모드 카운트 ÷ 전체 카운트 × 100 (HALF_UP, 소수 첫째 자리).
     *
     * @param count 모드 카운트
     * @param total 전체 카운트 (0 시 분모 0 방어 → 0.0 반환)
     * @return 비율 (%)
     */
    private BigDecimal computeRate(long count, long total) {
        if (total == 0L) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, RoundingMode.HALF_UP);
        }
        return BigDecimal.valueOf(count)
                .multiply(HUNDRED)
                .divide(BigDecimal.valueOf(total), RATE_SCALE, RoundingMode.HALF_UP);
    }
}
