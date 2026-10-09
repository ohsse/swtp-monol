package com.mo.swtp.opt.repository;

import com.mo.swtp.opt.dto.PredcEnergyBucketDto;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 시설 펌프 전력량 예측 커스텀 조회 인터페이스 (전력피크분석 4번섹션 기능2).
 *
 * <p>전력피크분석-4번섹션 PLAN1 (2026-06-05) — 펌프 PWQ(적산전력량) 예측 태그들의 1시간 버킷별 차분
 * (예측 전력량 kWh)을 조회한다. 동일 엔티티 ({@code predc_1m_h}) 를 가리키는 2번섹션
 * {@link PeakPredcCustomRepository} (분별 합산 임계 초과 시각 1건) · 10번섹션
 * {@link TagPredcRangeCustomRepository} (시점 범위 시계열 전체 조회) 와 의도가 다르다 — 본 메서드는
 * {@code GROUP BY tag_srl_no, date_trunc('hour', predc_dtm)} + {@code MAX(predc_val)-MIN(predc_val)} 차분
 * 버킷 집계 책임만 가진다. 사이클 간 자산 자동 원용 금지 정합 — 별도 Repository 로 분리한다
 * ({@code coding-discipline.md §3} 정밀한 수정).</p>
 *
 * <p>시계열 → 마스터 FK 금지 정책 ({@code .claude/rules/db/partitioning-and-retention.md §1}) 정합 —
 * {@code tag_srl_no} 는 {@code tag_m.tag_srl_no} 의 논리 참조이며 마스터 결합은 호출 Service 가 조합한다.</p>
 */
public interface PumpEnergyPredcCustomRepository {

    /**
     * PWQ 예측 태그의 1시간 버킷별 차분(예측 전력량 kWh)을 단일 native SQL 로 조회한다.
     *
     * <p>버킷은 {@code date_trunc('hour', predc_dtm)} 으로 시 단위 절삭하며, 각 버킷의 예측 전력량은 적산
     * 미터 예측값의 {@code MAX(predc_val) - MIN(predc_val)} 차분으로 산정한다. {@code predc_1m_h} 에는
     * {@code quality_cd}·{@code corr_val} 컬럼이 없으므로 {@code predc_val IS NOT NULL} 행만 집계한다
     * (실측 {@code rawdata_1m_h} 의 GOOD 필터·HLV 분기 미적용 — 전력피크분석-4번섹션 ANALYZE1 안건 3).</p>
     *
     * <p>음수 차분(적산 카운터 리셋·롤오버)은 본 쿼리에서 걸러내지 않으며, 호출 Service 가 단조증가 가정 위반으로
     * 생략 처리한다 (PLAN1 §기능2 버킷 가드). 시간(hour) 고정 버킷이므로 {@code dateTruncUnit} 파라미터는 받지
     * 않는다 ({@link com.mo.swtp.raw.repository.RawDataCustomRepository#findEnergyDeltaBuckets} 의 4값 가변
     * 단위와 의도 분리).</p>
     *
     * <p>{@code predc_dtm} 범위 조건으로 월 RANGE 파티션 프루닝 강제 + {@code idx_predc_1m_h_tag_time
     * (tag_srl_no, predc_dtm)} 인덱스 활용. 결과 정렬: {@code tag_srl_no ASC, base_dtm ASC}.</p>
     *
     * @param tagSrlNos PWQ 예측 태그 시리얼번호 목록 (빈 리스트 시 빈 List 반환)
     * @param startDtm  조회 시작 일시 (inclusive, 보통 현재 시 절삭값)
     * @param endDtm    조회 종료 일시 (exclusive, 보통 시작 + 24시간)
     * @return 태그별·버킷별 예측 전력량 차분 List (각 element 는 {@link PredcEnergyBucketDto})
     */
    List<PredcEnergyBucketDto> findEnergyDeltaBuckets(
            List<String> tagSrlNos, LocalDateTime startDtm, LocalDateTime endDtm);
}
