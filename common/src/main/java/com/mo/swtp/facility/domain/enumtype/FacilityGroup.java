package com.mo.swtp.facility.domain.enumtype;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 시설 그룹 분류 코드.
 *
 * <p>{@link FacilityType} 의 파생 분류 — 시설 유형을 업무 성격별로 묶는 상위 그룹이다.
 * type → group 은 1:1 불변 매핑이며 {@link FacilityType#getGroup()} 으로 노출된다.
 * <b>DB 컬럼으로 저장하지 않는다</b> — {@code facility_m} 테이블에 그룹 컬럼은 신설하지 않으며,
 * 응답 DTO ({@code FacilityDto.facilityGroupCd}) 가 {@code facility_type_cd} 로부터 계산하여 노출한다.</p>
 *
 * <p>그룹별 소속 유형 (시설_도메인_확장 ANALYZE1, 2026-06-08):</p>
 * <ul>
 *   <li>{@link #STORAGE} (저장시설) — PWTF·DWT·RSV</li>
 *   <li>{@link #OPERATION} (운영시설) — PRSF·WTBLD·CHMB·ACFB·POZB·FLTB·DEWB·SOLAR</li>
 *   <li>{@link #NETWORK} (계통시설) — POINT</li>
 * </ul>
 *
 * <p>JPA 매핑 미적용 — 파생 enum 으로 {@code @Enumerated} 컬럼 매핑 없음. 응답 직렬화는
 * {@code @Schema(implementation = FacilityGroup.class)} 로 enum name 을 노출한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum FacilityGroup {

    /** 저장시설 — 물을 저장하는 시설 (정수조·배수지·저수지). */
    STORAGE("저장시설"),

    /** 운영시설 — 정수 공정·부대 운영 시설 (가압장·약품동·여과지동·활성탄여과지·전오존동·탈수기동·태양광·송수동). */
    OPERATION("운영시설"),

    /** 계통시설 — 관로 계통 분기·계측 지점 (분기점). */
    NETWORK("계통시설");

    /** 한글 논리명 (예: "저장시설", "운영시설", "계통시설") */
    private final String description;
}
