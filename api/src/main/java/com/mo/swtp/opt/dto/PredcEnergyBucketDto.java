package com.mo.swtp.opt.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * AI 예측 시계열 전력량 버킷 집계 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.opt.repository.PumpEnergyPredcCustomRepository#findEnergyDeltaBuckets} 의 반환
 * element 로, {@code date_trunc('hour', predc_dtm)} GROUP BY 집계 결과 행을 1:1 매핑한다.
 * 응답 DTO 가 아닌 Service 내부 전송 전용이므로 Swagger 노출 대상 외
 * ({@link com.mo.swtp.raw.dto.RawDataBucketDto} 선례 동형, 전력피크분석-4번섹션 PLAN1 §기능2 내부 전송 DTO).</p>
 *
 * @param tagSrlNo 태그 시리얼번호 ({@code tag_m.tag_srl_no} 논리 참조)
 * @param baseDtm  버킷 시작 일시 ({@code date_trunc('hour', predc_dtm)} 결과 — 시 단위 절삭값)
 * @param aggrVal  버킷 예측 전력량 ({@code MAX(predc_val)-MIN(predc_val)} 차분)
 */
public record PredcEnergyBucketDto(
        String tagSrlNo,
        LocalDateTime baseDtm,
        BigDecimal aggrVal
) {
}
