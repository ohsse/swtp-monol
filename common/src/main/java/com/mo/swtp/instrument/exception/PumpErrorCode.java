package com.mo.swtp.instrument.exception;

import com.mo.swtp.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 송수펌프 도메인 에러 코드 — common 모듈에 위치.
 *
 * <p>도메인 엔티티 ({@code com.mo.swtp.instrument.domain.Pump}) 가 비즈니스 규칙 위반 시 직접
 * {@code RestApiException} 으로 던지는 ErrorCode 다. {@code com.mo.swtp.common.exception.JwtErrorCode}
 * 와 동일한 common 모듈 위치 패턴 — 공통 인프라/도메인 자체 ErrorCode 가 common 에 위치한다.</p>
 *
 * <p>책임 경계 — {@code InstrumentErrorCode (api 모듈)} 와 분리:</p>
 * <ul>
 *   <li>{@link PumpErrorCode} (common) — 도메인 규칙 위반 (조합 유효성 등)</li>
 *   <li>{@code InstrumentErrorCode} (api) — API CRUD 진입점 에러 (FK 위반·NM 중복·equip_type 불일치)</li>
 * </ul>
 *
 * <p>도입 배경: pump_drive_type ANALYZE1 (2026-05-20) — 펌프 구동 방식 (INVERTER_DRIVE/RATED_DRIVE)
 * 도입에 따른 도메인 안전 조합 검증.</p>
 */
@Getter
@RequiredArgsConstructor
public enum PumpErrorCode implements ErrorCode {

    /**
     * 펌프 구동 방식 + 조작 유형 무효 조합 — {@code RATED_DRIVE + AUTO_CAPABLE} (400).
     *
     * <p>물리 제약: 정격 펌프(고정속, ON/OFF 만)는 AI 자동 모드(가변 주파수 제어)와 양립 불가.
     * {@code Pump.create()} / {@code Pump.changePumpSelfColumns()} 진입 시 발생하며,
     * {@code .claude/rules/ot-integration.md §5 ⚠️ 절대 금지} (검증 건너뛴 채 재시도 금지) 직결 —
     * 미차단 시 가변속 명령이 정격 PLC 송신 → CircuitBreaker 연속 실패 → OT 안전 위협.</p>
     *
     * <p>4 조합 유효성 (pump_drive_type ANALYZE1 안건 5, 2026-05-20):</p>
     * <ul>
     *   <li>INVERTER_DRIVE + AUTO_CAPABLE — 유효</li>
     *   <li>INVERTER_DRIVE + SEMI_AUTO_CAPABLE — 유효</li>
     *   <li>RATED_DRIVE + AUTO_CAPABLE — <strong>무효 (본 ErrorCode)</strong></li>
     *   <li>RATED_DRIVE + SEMI_AUTO_CAPABLE — 유효</li>
     * </ul>
     */
    INVALID_PUMP_DRIVE_OPRTNG_COMBINATION(400);

    private final int httpStatus;
}
