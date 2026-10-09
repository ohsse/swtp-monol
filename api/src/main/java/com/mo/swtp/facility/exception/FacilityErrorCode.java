package com.mo.swtp.facility.exception;

import com.mo.swtp.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 시설 도메인 에러 코드.
 *
 * <p>시설물관리기능 ANALYZE1 (2026-05-11) 도입. 운전현황분석-4번섹션 ANALYZE1 (2026-05-21) 확장 — 5종 분기:</p>
 * <ul>
 *   <li>{@link #FACILITY_NOT_FOUND} — 단건 조회·수정·삭제 시 facilityId 미존재 또는 비활성 (404)</li>
 *   <li>{@link #DUPLICATE_FACILITY_NM} — 등록·수정 시 facility_nm UNIQUE 충돌 (409)</li>
 *   <li>{@link #FACILITY_TYPE_MISMATCH} — PUT/DELETE 호출 시 path 대상 시설의 facility_type_cd 와
 *       request body discriminator 불일치 (400)</li>
 *   <li>{@link #INVALID_PARENT_FACILITY_ID} — parentFacilityId 미존재 또는 자기 자신 참조 (400)</li>
 *   <li>{@link #UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS} — 운영 현황 조회 시 facility_type_cd 가
 *       지원 종류(PWTF/DWT/PRSF) 외 (RSV/POINT) 인 경우 (400) — 운전현황분석-4번섹션 ANALYZE1</li>
 *   <li>{@link #INVALID_SEARCH_PERIOD} — 사용량 조회 시 집계단위·기간 파라미터 검증 실패 (400) — 시설별사용량-2번섹션 ANALYZE1.
 *       시작일/종료일 누락·역전, 집계단위 누락·YEAR(미지원), 조회기간 13개월(396일) 초과</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum FacilityErrorCode implements ErrorCode {

    FACILITY_NOT_FOUND(404),
    DUPLICATE_FACILITY_NM(409),
    FACILITY_TYPE_MISMATCH(400),
    INVALID_PARENT_FACILITY_ID(400),
    UNSUPPORTED_FACILITY_TYPE_FOR_OPERATING_STATUS(400),
    INVALID_SEARCH_PERIOD(400);

    private final int httpStatus;
}
