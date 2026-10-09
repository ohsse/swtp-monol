package com.mo.swtp.instrument.repository;

import com.mo.swtp.instrument.dto.PumpCtrlHistoryDto;
import com.mo.swtp.proc.domain.enumtype.AiDrvnModeCode;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 펌프 제어 이력 Querydsl 커스텀 조회 인터페이스.
 *
 * <p>송수펌프 제어이력 2·3번 섹션 조회 — 운전모드별 카운트 집계(2번)와 시간 역순 목록(3번)을 제공한다.
 * 두 조회 모두 {@code ctrl_dtm} 범위 조건으로 월 RANGE 파티션 프루닝을 강제한다 (제어이력 재도입 PLAN1).</p>
 */
public interface PumpCtrlHistoryCustomRepository {

    /**
     * 조회기간 동안의 제어 이력을 운전모드별로 카운트한다 (2번섹션 AI 운영 현황).
     *
     * <p>수동 제어({@code ai_drvn_mod IS NULL})는 집계에서 제외한다. 카운트가 0건인 모드는 결과 Map 에
     * 포함되지 않으므로, 3종 전체 표출은 Service 에서 보강한다.</p>
     *
     * @param startDtm 조회 시작 일시 (inclusive)
     * @param endDtm   조회 종료 일시 (exclusive)
     * @return 운전모드 → 카운트 Map (카운트 0건 모드는 미포함)
     */
    Map<AiDrvnModeCode, Long> countByAiDrvnMode(LocalDateTime startDtm, LocalDateTime endDtm);

    /**
     * 조회기간 동안의 제어 이력 목록을 시간 역순으로 조회한다 (3번섹션 제어 이력).
     *
     * <p>{@code instrument_m}(펌프명) 조인 + 제어 태그번호({@code tag_se_cd='CMD' AND io_cd='OUTPUT'})
     * 상관 서브쿼리를 포함한다. 제어 태그 미존재 시 태그번호는 null.</p>
     *
     * @param startDtm 조회 시작 일시 (inclusive)
     * @param endDtm   조회 종료 일시 (exclusive)
     * @return 제어 이력 행 목록 ({@code ctrl_dtm} 내림차순)
     */
    List<PumpCtrlHistoryDto> findCtrlHistoryList(LocalDateTime startDtm, LocalDateTime endDtm);
}
