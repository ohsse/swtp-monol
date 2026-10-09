package com.mo.swtp.raw.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * SCADA 원시 데이터 월별 최대 피크 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.raw.repository.RawDataCustomRepository#findMonthlyMaxMinuteSumElpwr} 의 반환
 * element 로, 분(分)별 전체 PWI(순시전력) 태그 합산값({@code SUM(COALESCE(corr_val, raw_val))}) 을 월
 * 버킷({@code date_trunc('month', acq_dtm)}) 단위로 MAX 한 결과 행을 1:1 매핑한다. 분별 합산 후 월별 MAX 의
 * 중첩 집계 결과이므로 ({@code MAX_over_month(SUM_over_facilities)}), {@link RawDataBucketSumDto}(버킷 전역
 * 합산 — 적산전력량 차분의 합) 와 의미가 다른 사용량트렌드-3번섹션 전용 프로젝션이다 (사용량트렌드-3번섹션
 * PLAN1 §구현 방향 1).</p>
 *
 * <p>응답 DTO 가 아닌 Service 내부 전송 전용이므로 Swagger 노출 대상 외 ({@link RawDataBucketSumDto} 선례
 * 동형). 결과는 데이터가 존재하는 월만 포함(sparse)하며, 6개 월 슬롯 채움은 호출 Service 의 책임이다.</p>
 *
 * @param baseDtm 월 버킷 시작 일시 ({@code date_trunc('month', acq_dtm)} 결과 — 해당 월 1일 00:00)
 * @param peakVal 월별 최대 피크 (그 달의 분별 전체 PWI 합산값 중 최댓값, kW)
 */
public record RawDataBucketPeakDto(
        LocalDateTime baseDtm,
        BigDecimal peakVal
) {
}
