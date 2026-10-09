package com.mo.swtp.proc.exception;

import com.mo.swtp.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 공정/제어대상 도메인 에러 코드.
 *
 * <p>송수펌프제어분석-2번섹션 ANALYZE2 안건 5·6 + PLAN2 결정 (2026-05-20).</p>
 */
@Getter
@RequiredArgsConstructor
public enum ProcErrorCode implements ErrorCode {

    /** 공정/제어대상 미존재 */
    PROC_NOT_FOUND(404),

    /** AI 운전모드 코드 무효 (enum 미매칭) */
    INVALID_AI_DRVN_MOD(400),

    /** 동시 변경 — 부분 UNIQUE 인덱스 충돌 (이력 활성 행 1건 강제 위반) */
    AI_MODE_CONCURRENT_UPDATE(409),

    /** proc_id 형식 무효 — 정규식 ^[A-Z][A-Z0-9_]*$ 미매칭 */
    INVALID_PROC_ID_FORMAT(400);

    private final int httpStatus;
}
