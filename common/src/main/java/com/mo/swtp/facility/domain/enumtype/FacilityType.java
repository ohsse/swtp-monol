package com.mo.swtp.facility.domain.enumtype;

import java.util.Arrays;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 시설 자식 종류 코드.
 *
 * <p>{@code facility_m} 단일 마스터의 {@code facility_type_cd} DiscriminatorColumn 매핑 enum.
 * JPA {@code @Inheritance(InheritanceType.JOINED)} + {@code @DiscriminatorColumn} 패턴에서 자식 종류를 식별한다.</p>
 *
 * <p>각 유형은 한글 설명({@link #description})과 상위 그룹({@link #group})을 보유한다.
 * type → group 은 1:1 불변 매핑 — {@link FacilityGroup} 파생 분류는 {@code facility_m} 에 컬럼을 신설하지 않고
 * {@link #getGroup()} 으로 계산 노출한다 (시설_도메인_확장 ANALYZE1, 2026-06-08).</p>
 *
 * <p>도메인 룰: AI 운전 모드 평가·제어 명령 발행 등 자식 종류별 도메인 룰이 다른 시나리오에서는
 * 부모 다형성 조회 시 반드시 {@code facility_type_cd} 필터를 명시한다 — 자식 종류마다
 * 인터록 룰·운전 모드 평가 기준이 다르므로 부모 전체 조회는 도메인 룰 위반 위험.
 * 1차 정의처: {@code .claude/rules/entity-patterns.md} §JPA JOINED + DiscriminatorColumn 다형성 패턴
 * §도메인 룰 — {@code facility_type_cd} 필터 강제.</p>
 *
 * <p>도입 이력:</p>
 * <ul>
 *   <li>마스터도메인설계 ANALYZE1 Round 2 (2026-05-02) — PWTF·DWT·RSV 도입</li>
 *   <li>송수펌프제어_운전현황분석 ANALYZE1 (2026-05-07) — POINT 도입</li>
 *   <li>송수펌프제어분석 ANALYZE1 (2026-05-08) — PRSF 도입</li>
 *   <li>시설_도메인_확장 ANALYZE1 (2026-06-08) — 생성자 필드(description·group) 도입 +
 *       운영시설 7종(WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR) 추가, {@link FacilityGroup} 그룹 분류 신설</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum FacilityType {

    /** 정수조 (Purified Water Tank Facility) */
    PWTF("정수조", FacilityGroup.STORAGE),

    /** 배수지 (Distribution Water Tank) */
    DWT("배수지", FacilityGroup.STORAGE),

    /** 저수지 (Reservoir) */
    RSV("저수지", FacilityGroup.STORAGE),

    /**
     * 관로 계측 분기점 (Sensor Point).
     * FR-PMP-002 (운전현황 분석) BRANCH_MEAS_PREDC_DATA 응답 매핑 — 송수펌프제어_운전현황분석 ANALYZE1 (2026-05-07) 도입.
     */
    POINT("분기점", FacilityGroup.NETWORK),

    /**
     * 가압장 (Pressure Booster Station Facility).
     * 송수펌프제어분석 ANALYZE1 (2026-05-08) 도입 — PWTF 와 동일한 정수지 단위 모니터링 대상,
     * AI 운전 모드 평가는 시설 종류별 독립 (시설 단위 별도 평가, {@code ot-integration.md §5} 정합).
     * 본 사이클은 skeleton 만 도입 ({@code prsf_m} 자식 테이블, 자식 전용 컬럼 0건).
     */
    PRSF("가압장", FacilityGroup.OPERATION),

    /**
     * 송수동 (Water Transmission Building).
     * 시설_도메인_확장 ANALYZE1 (2026-06-08) 도입 — 운영시설. skeleton 만 도입 ({@code wtbld_m}, 자식 전용 컬럼 0건).
     */
    WTBLD("송수동", FacilityGroup.OPERATION),

    /**
     * 약품동 (Chemical Building).
     * 시설_도메인_확장 ANALYZE1 (2026-06-08) 도입 — 운영시설. skeleton 만 도입 ({@code chmb_m}, 자식 전용 컬럼 0건).
     */
    CHMB("약품동", FacilityGroup.OPERATION),

    /**
     * 활성탄여과지 (Activated Carbon Filter Building).
     * 시설_도메인_확장 ANALYZE1 (2026-06-08) 도입 — 운영시설. skeleton 만 도입 ({@code acfb_m}, 자식 전용 컬럼 0건).
     */
    ACFB("활성탄여과지", FacilityGroup.OPERATION),

    /**
     * 전오존동 (Pre-Ozonation Building).
     * 시설_도메인_확장 ANALYZE1 (2026-06-08) 도입 — 운영시설. skeleton 만 도입 ({@code pozb_m}, 자식 전용 컬럼 0건).
     */
    POZB("전오존동", FacilityGroup.OPERATION),

    /**
     * 여과지동 (Filtration Building).
     * 시설_도메인_확장 ANALYZE1 (2026-06-08) 도입 — 운영시설. skeleton 만 도입 ({@code fltb_m}, 자식 전용 컬럼 0건).
     */
    FLTB("여과지동", FacilityGroup.OPERATION),

    /**
     * 탈수기동 (Dewatering Building).
     * 시설_도메인_확장 ANALYZE1 (2026-06-08) 도입 — 운영시설. skeleton 만 도입 ({@code dewb_m}, 자식 전용 컬럼 0건).
     */
    DEWB("탈수기동", FacilityGroup.OPERATION),

    /**
     * 태양광 (Solar Power Facility).
     * 시설_도메인_확장 ANALYZE1 (2026-06-08) 도입 — 운영시설. skeleton 만 도입 ({@code solar_m}, 자식 전용 컬럼 0건).
     */
    SOLAR("태양광", FacilityGroup.OPERATION);

    /** 한글 논리명 (예: "정수조", "송수동") */
    private final String description;

    /** 상위 그룹 분류 (저장/운영/계통) — type → group 1:1 불변 매핑 */
    private final FacilityGroup group;

    /**
     * 지정한 그룹에 속한 시설 유형 목록을 반환한다.
     *
     * <p>{@code group → 소속 type 목록} 파생 SSOT — 그룹별 유형을 호출처에 하드코딩하지 않고
     * {@link #group} 필드 기준으로 단일 도출한다. 예: {@code typesOf(FacilityGroup.OPERATION)} =
     * [PRSF, WTBLD, CHMB, ACFB, POZB, FLTB, DEWB, SOLAR] (8종).</p>
     *
     * <p>{@code facility_type_cd} 필터 강제 룰 정합 — 그룹 단위 다형성 조회는 본 메서드로 전개한
     * {@code facility_type_cd IN (...)} 조건으로 수행하여 다른 그룹 자식의 혼입을 차단한다
     * (설비별사용량-2번섹션 ANALYZE1, 2026-06-09).</p>
     *
     * @param group 시설 그룹 (저장/운영/계통)
     * @return 해당 그룹에 속한 시설 유형 목록 (불변)
     */
    public static List<FacilityType> typesOf(FacilityGroup group) {
        return Arrays.stream(values()).filter(t -> t.group == group).toList();
    }
}
