package com.mo.swtp.raw.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 시설 단위 분(分)별 합산 집계 Service 내부 전송 DTO.
 *
 * <p>{@link com.mo.swtp.raw.repository.RawDataCustomRepository#findFacilityLatestMinuteSumElpwr} ·
 * {@link com.mo.swtp.raw.repository.RawDataCustomRepository#findFacilityBucketPeakElpwr} 의 반환 element 로,
 * 태그→운영시설 루트 매핑({@code unnest} 인라인) 으로 분(分)별 시설합을 구성한 뒤의 집계 결과 행을 1:1 매핑한다.</p>
 *
 * <ul>
 *   <li>마지막값 조회 — {@code dtm} = 마지막 분의 {@code acq_dtm}, {@code value} = 그 분의 시설합 PWI (kW)</li>
 *   <li>최대전력 조회 — {@code dtm} = 최대전력 발생 버킷 시작 시각, {@code value} = 버킷 시설합 PWI MAX (kW)</li>
 * </ul>
 *
 * <p>응답 DTO 가 아닌 Service 내부 전송 전용이므로 Swagger 노출 대상 외 ({@link RawDataBucketDto} ·
 * {@link RawDataLatestDto} 선례 동형, 시설별사용량-2번섹션 PLAN1 §신규 컴포넌트).</p>
 *
 * @param facilityId 운영시설 루트 ID ({@code facility_m.facility_id} 논리 매핑 — 시계열 → 마스터 FK 금지 정합)
 * @param dtm        집계 결과 시각 (마지막값: 마지막 분 / 최대전력: 발생 버킷 시작 시각)
 * @param value      집계값 (kW, 분별 시설합 PWI 의 마지막값 또는 버킷 MAX)
 */
public record RawDataFacilitySumDto(
        String facilityId,
        LocalDateTime dtm,
        BigDecimal value
) {
}
