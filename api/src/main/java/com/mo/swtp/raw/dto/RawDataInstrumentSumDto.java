package com.mo.swtp.raw.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 계측기(설비) 단위 분(分)별 시설합 순시전력 집계 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.raw.repository.RawDataCustomRepository#findInstrumentMinuteSumElpwr} 의 반환 element 로,
 * 태그→계측기 매핑({@code unnest(tags, instruments)} 인라인) 으로 분(分)별 설비합을 구성한 뒤의 집계 결과 행을
 * 1:1 매핑한다. 한 설비가 PWI 태그를 다건 보유하면 같은 {@code acq_dtm} 에서 합산된 값이며, 마지막 분 1건만
 * 반환하는 {@link RawDataFacilitySumDto} 와 달리 본 DTO 는 조회기간 전체 분(分) 시계열을 1행씩 담는다
 * (설비별사용량-7번섹션 PLAN1 §구현 방향 3).</p>
 *
 * <p>응답 DTO 가 아닌 Service 내부 전송 전용이므로 Swagger 노출 대상 외 ({@link RawDataFacilitySumDto} ·
 * {@link RawDataBucketDto} 선례 동형).</p>
 *
 * @param instrumentId 계측기 ID ({@code instrument_m.instrument_id} 논리 매핑 — 시계열 → 마스터 FK 금지 정합)
 * @param dtm          측정 분(分) 시각 ({@code acq_dtm})
 * @param value        그 분의 설비합 순시전력 (kW, GOOD 품질 PWI 태그 {@code COALESCE(corr_val, raw_val)} 합산)
 */
public record RawDataInstrumentSumDto(
        String instrumentId,
        LocalDateTime dtm,
        BigDecimal value
) {
}
