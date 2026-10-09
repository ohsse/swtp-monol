package com.mo.swtp.tag.exception;

import com.mo.swtp.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 태그 도메인 에러 코드.
 *
 * <p>{@code .claude/rules/exception-patterns.md} §2 ErrorCode 구현 enum 필드 규약 준수 —
 * {@code httpStatus(int)} 만 보유하며 {@code String message} 필드는 금지된다
 * ({@code check-errorcode-contract.sh} 훅 차단 대상).</p>
 *
 * <p>사용자 표기 문자열은 프론트엔드가 {@code errorCode.name()} 으로 명세 기반 매핑한다.</p>
 */
@Getter
@RequiredArgsConstructor
public enum TagErrorCode implements ErrorCode {

    /** 태그 미존재 — 단건 조회·수정·삭제 시 */
    TAG_NOT_FOUND(404),

    /** 태그 시리얼번호 중복 — 등록 시 동일 PK 존재 */
    DUPLICATE_TAG_SRL_NO(409),

    /** 계측기 ID 무효 — instrument_m 미존재 또는 NULL */
    INVALID_INSTRUMENT_ID(400),

    /**
     * FQI 측정 유형 등록 차단 — FQI 는 {@code PumpDriveType.INVERTER_DRIVE} 펌프 계측기에만 허용.
     *
     * <p>{@code RATED_DRIVE} 펌프 + 비-Pump 계측기 모두 차단 ({@code ot-integration.md §5 ⚠️ 절대 금지} 직결 —
     * 정격 펌프는 가변속 명령 송신 불능, 비-Pump 계측기는 주파수 제어 대상 아님).
     * 도입: tag_frequency_추가 ANALYZE1 안건 4 (2026-05-20).</p>
     */
    FQI_ONLY_ALLOWED_FOR_INVERTER_PUMP(400);

    private final int httpStatus;
}
