package com.mo.swtp.instrument.exception;

import com.mo.swtp.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 계측기 도메인 에러 코드.
 *
 * <p>계측기관리CRUD ANALYZE1·PLAN1 (2026-05-12) 도입. 4종 분기:</p>
 * <ul>
 *   <li>{@link #INSTRUMENT_NOT_FOUND} — 단건 조회·수정·삭제 시 instrumentId 미존재 (404)</li>
 *   <li>{@link #DUPLICATE_INSTRUMENT_NM} — 등록·수정 시 {@code (facility_id, instrument_nm)} 복합 UNIQUE 충돌 (409)</li>
 *   <li>{@link #EQUIP_TYPE_MISMATCH} — PUT/DELETE 호출 시 path 대상 계측기의 {@code equip_type_cd} 와
 *       request body discriminator 불일치 (400). FacilityErrorCode.FACILITY_TYPE_MISMATCH 동일 패턴</li>
 *   <li>{@link #INVALID_FACILITY_ID} — 등록·수정 시 facilityId 미존재 (400, FK 위반 500 회피)</li>
 *   <li>{@link #INVALID_INQ_PERIOD} — 시계열 조회 시 조회 단위·기간 파라미터 결측 또는 from &gt; to 역전 (400).
 *       송수펌프 가동이력 3번섹션 시계열 조회 도입 (송수펌프가동이력_3번섹션 PLAN1, 2026-06-02)</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum InstrumentErrorCode implements ErrorCode {

    INSTRUMENT_NOT_FOUND(404),
    DUPLICATE_INSTRUMENT_NM(409),
    EQUIP_TYPE_MISMATCH(400),
    INVALID_FACILITY_ID(400),
    INVALID_INQ_PERIOD(400);

    private final int httpStatus;
}
