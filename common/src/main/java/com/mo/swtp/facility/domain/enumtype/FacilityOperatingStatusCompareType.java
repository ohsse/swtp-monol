package com.mo.swtp.facility.domain.enumtype;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 운영 현황 시계열 비교 옵션 — 운전현황분석 5번 섹션.
 *
 * <p>금일 시계열({@code 00:00 ~ 현재시간}) 과 함께 응답할 비교 기간을 선택한다.
 * 본 사이클은 두 옵션만 지원 (운전현황분석-5번섹션 ANALYZE1 사용자 결정):</p>
 * <ul>
 *   <li>{@link #YESTERDAY} — 어제 하루({@code -1d 00:00 ~ 23:59:59})</li>
 *   <li>{@link #LAST_WEEK} — 일주일 전 하루({@code -7d 00:00 ~ 23:59:59})</li>
 * </ul>
 *
 * <p>지난달 평균({@code LAST_MONTH_AVG}) 옵션은 별도 데이터 집계 사이클에서 처리한다
 * (운전현황분석-5번섹션 ANALYZE1 §향후 사이클).</p>
 *
 * <p>본 enum 은 REST 요청 query parameter 의 코드값으로 사용되며 DB 컬럼 매핑 대상은 아니다 —
 * {@code @Enumerated} 적용 위치 없음.</p>
 */
@Schema(description = "운영 현황 시계열 비교 옵션 — 운전현황분석 5번 섹션")
public enum FacilityOperatingStatusCompareType {

    /** 전일 (어제 00:00 ~ 23:59:59). */
    @Schema(description = "전일 — 어제 하루의 시계열")
    YESTERDAY,

    /** 지난주 동일 요일 (7일 전 00:00 ~ 23:59:59). */
    @Schema(description = "지난주 — 7일 전 하루의 시계열")
    LAST_WEEK
}
