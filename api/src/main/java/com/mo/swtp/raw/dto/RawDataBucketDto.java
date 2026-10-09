package com.mo.swtp.raw.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SCADA 원시 데이터 시계열 버킷 집계 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.raw.repository.RawDataCustomRepository#findEnergyDeltaBuckets} ·
 * {@link com.mo.swtp.raw.repository.RawDataCustomRepository#findAvgValueBuckets} 의 반환 element 로,
 * {@code date_trunc(:unit, acq_dtm)} GROUP BY 집계 결과 행을 1:1 매핑한다.
 * 응답 DTO 가 아닌 Service 내부 전송 전용이므로 Swagger 노출 대상 외
 * ({@link RawDataLatestDto} 선례 동형, 송수펌프가동이력_3번섹션 PLAN1 §구현 방향 3).</p>
 *
 * @param tagSrlNo 태그 시리얼번호 ({@code tag_m.tag_srl_no} 논리 참조)
 * @param baseDtm  버킷 시작 일시 ({@code date_trunc} 결과 — 시/일/월/년 단위 절삭값)
 * @param aggrVal  버킷 집계값 (전력량: {@code MAX(raw_val)-MIN(raw_val)} 차분 / 주파수: {@code AVG(COALESCE(corr_val,raw_val))})
 */
public record RawDataBucketDto(
        String tagSrlNo,
        LocalDateTime baseDtm,
        BigDecimal aggrVal
) {
}
