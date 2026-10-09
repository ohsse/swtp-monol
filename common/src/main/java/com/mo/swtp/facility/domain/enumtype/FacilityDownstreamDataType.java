package com.mo.swtp.facility.domain.enumtype;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 재귀 하위 시설 시계열 표출 데이터 종류 — 운전현황분석 8번 섹션.
 *
 * <p>프론트의 수요량/관압/수위 탭을 단일 엔드포인트의 query parameter 로 분기한다. 각 종류는 표출 대상·
 * 측정기·태그가 다르다 (운전현황분석-8번섹션 PLAN1 §dataType 매핑):</p>
 * <ul>
 *   <li>{@link #DEMAND} — 수요량. 표출대상(PWTF·POINT) 의 유출 FLWMTR 의 FRI(유출유량). 표출대상 시설당 1 시리즈.</li>
 *   <li>{@link #PRESSURE} — 관압. 표출대상(PWTF·POINT) 의 유출 FLWMTR 의 PRI(유출수압). 표출대상 시설당 1 시리즈.</li>
 *   <li>{@link #LEVEL} — 수위. 표출대상의 자식 DWT 의 수위계(LVMTR) 의 LEI(수위). 수위계당 1 시리즈.</li>
 * </ul>
 *
 * <p>{@code DEMAND}·{@code PRESSURE} 는 응답 측 {@code FacilityDownstreamMeasureDto} 로, {@code LEVEL} 은
 * {@code FacilityDownstreamLevelDto} 로 직렬화된다 (Jackson 다형성 discriminator). 본 enum 은 REST 요청
 * query parameter 의 코드값으로 사용되며 DB 컬럼 매핑 대상은 아니다 — {@code @Enumerated} 적용 위치 없음
 * (5번 섹션 {@link FacilityOperatingStatusCompareType} 동일 정책).</p>
 */
@Schema(description = "재귀 하위 시설 시계열 표출 데이터 종류 — 운전현황분석 8번 섹션")
public enum FacilityDownstreamDataType {

    /** 수요량 — 표출대상(PWTF·POINT) 유출 FLWMTR 의 FRI(유출유량). */
    @Schema(description = "수요량 — 표출대상 유출 유량계의 FRI(유출유량)")
    DEMAND,

    /** 관압 — 표출대상(PWTF·POINT) 유출 FLWMTR 의 PRI(유출수압). */
    @Schema(description = "관압 — 표출대상 유출 유량계의 PRI(유출수압)")
    PRESSURE,

    /** 수위 — 표출대상의 자식 DWT 의 수위계(LVMTR) 의 LEI(수위). */
    @Schema(description = "수위 — 자식 배수지 수위계의 LEI(수위)")
    LEVEL
}
