package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.dto.TagPredictionMatchDto;
import java.util.List;

/**
 * AI 예측 시계열 태그 근접매칭 커스텀 조회 인터페이스.
 *
 * <p>송수펌프제어분석-7번섹션 PLAN1 §2 — 활성 시설 태그별 "현황 최신 계측시각 + 1시간" 에 가장 근접한
 * 예측행 1건을 일괄 조회한다. 섹션 3 의 단순 DISTINCT ON ({@code RawDataCustomRepository}) 보다 복잡 —
 * 태그별 기준시각 산정 (latest_meas CTE) + 대칭 범위 근접 매칭 (CROSS JOIN LATERAL) 의 단일 native SQL.</p>
 *
 * <p>섹션 7 자체 완결 — {@code latest_meas} CTE 가 {@code rawdata_1m_h} 를 직접 참조하며 섹션 3 자산
 * ({@code RawDataCustomRepository.findLatestByTagSrlNos}) 무참조 (PLAN1 §2 — 섹션 3 자산 재사용 금지).</p>
 *
 * <p>시계열 → 마스터 FK 금지 정책 ({@code .claude/rules/db/partitioning-and-retention.md §1}) 정합.
 * 마스터 결합은 호출 Service 가 별도로 조합한다.</p>
 */
public interface TagPredictionCustomRepository {

    /**
     * 태그 시리얼번호 목록의 각 태그별 "현황 최신 acq_dtm + 1시간" 에 가장 근접한 예측행 1건을 일괄 조회한다.
     *
     * <p>구현은 다음 단일 native SQL 흐름으로 작성된다 (PLAN1 §2):</p>
     * <ol>
     *   <li>{@code latest_meas} CTE — {@code rawdata_1m_h} 에서 태그별 최신 {@code acq_dtm} 1건씩 추출
     *       ({@code idx_rawdata_1m_h_tag_time (tag_srl_no, acq_dtm DESC)} 활용)</li>
     *   <li>{@code CROSS JOIN LATERAL} — 각 태그별 {@code (acq_dtm + 1h) ± windowMinutes} 범위 내 예측행 중
     *       {@code ABS(predc_dtm - target)} 최소값 1건 추출
     *       ({@code idx_predc_1m_h_tag_time (tag_srl_no, predc_dtm)} ASC forward scan)</li>
     * </ol>
     *
     * <p>윈도우 내 예측행이 없는 태그는 LATERAL 미반환 → 응답 List 에 포함되지 않는다 (Service 가 null 매핑).</p>
     *
     * @param tagSrlNos     태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param windowMinutes 근접 매칭 윈도우 (분 단위, {@code application-common.yml} {@code opt.prediction.match-window-minutes})
     * @return 태그별 근접 예측행 1건씩 (각 element 는 {@link TagPredictionMatchDto})
     */
    List<TagPredictionMatchDto> findNearestByTagSrlNos(List<String> tagSrlNos, int windowMinutes);
}
