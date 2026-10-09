package com.mo.swtp.raw.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SCADA 원시 데이터 시계열 버킷 전역 합산 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.raw.repository.RawDataCustomRepository#findEnergyDeltaBucketsTotal} 의 반환
 * element 로, 태그별 선차분({@code MAX(raw_val)-MIN(raw_val)}) 을 버킷({@code date_trunc(:unit, acq_dtm)})
 * 단위로 전역 합산한 결과 행을 1:1 매핑한다. 태그 차원이 SQL 단계에서 이미 소거된 단일 시계열 합산값이므로
 * {@link RawDataBucketDto}(태그별·{@code aggrVal}) 와 별개 — 합산을 DB 에서 수행해 대용량 행 materialize 를
 * 회피하기 위한 사용량트렌드-2번섹션 전용 프로젝션 (사용량트렌드-2번섹션 PLAN1 §구현 방향 1·2).</p>
 *
 * <p>응답 DTO 가 아닌 Service 내부 전송 전용이므로 Swagger 노출 대상 외 ({@link RawDataBucketDto} 선례 동형).</p>
 *
 * @param baseDtm  버킷 시작 일시 ({@code date_trunc} 결과 — 시/일/월 단위 절삭값)
 * @param totalVal 버킷 전역 합산 전력량 (전체 PWQ 태그 {@code MAX(raw_val)-MIN(raw_val)} 차분의 버킷별 합, kWh)
 */
public record RawDataBucketSumDto(
        LocalDateTime baseDtm,
        BigDecimal totalVal
) {
}
