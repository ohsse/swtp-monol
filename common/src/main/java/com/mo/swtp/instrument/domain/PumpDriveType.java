package com.mo.swtp.instrument.domain;

/**
 * 송수펌프 구동 방식 — 펌프의 물리적 설계값.
 *
 * <p>제조사 설계에 따라 인버터 드라이브와 정격 드라이브로 분류된다. 한 펌프는 두 구동 방식 중 하나만
 * 보유하며 (상호 배타), 마스터 데이터 단계에서 결정된다.</p>
 *
 * <p>{@link PumpOprtngType} (조작유형 — AUTO_CAPABLE/SEMI_AUTO_CAPABLE) 과는 의미 직교축이다 —
 * 본 enum 은 펌프의 구동 방식 (가변속 인버터 ⊕ 고정속 정격), 조작유형 enum 은 펌프의 제어 방식
 * (자동/반자동 능력값). 4 조합 중 {@code RATED_DRIVE + AUTO_CAPABLE} 만 도메인 무효 (정격 펌프 가변속
 * 불가 → AI 자동 제어 명령 송신 불능) 이며, {@link Pump#create} / {@link Pump#changePumpSelfColumns}
 * 진입 시 {@code InstrumentErrorCode#INVALID_PUMP_DRIVE_OPRTNG_COMBINATION} 으로 차단된다
 * ({@code .claude/rules/ot-integration.md §5 ⚠️ 절대 금지} 직결 — pump_drive_type ANALYZE1 안건 5, 2026-05-20).</p>
 *
 * <p>{@code _DRIVE} 접미사로 구동 방식임을 명시한다. 마이그레이션 백필 기본값은 {@link #RATED_DRIVE}
 * (fail-safe — INVERTER 잘못 분류 시 가변속 명령이 정격 PLC 송신 → OT 안전 위협. RATED 잘못 분류 시
 * AI 자동 제어 비활성화일 뿐. 비대칭 위험으로 안전 쪽 실패 채택).</p>
 *
 * <p>도입: pump_drive_type ANALYZE1 안건 2·3 (2026-05-20). DB 매핑: {@code pump_m.drive_type_cd}
 * (DOM_CODE_20, {@code VARCHAR(20)} NOT NULL, {@code @Enumerated(EnumType.STRING)}).</p>
 */
public enum PumpDriveType {

    /**
     * 인버터 펌프 — 가변속 구동 (VFD 주파수 제어).
     * AI 자동 제어 가능 ({@link PumpOprtngType#AUTO_CAPABLE}·{@link PumpOprtngType#SEMI_AUTO_CAPABLE} 모두 허용).
     */
    INVERTER_DRIVE,

    /**
     * 정격 펌프 — 고정속 구동 (ON/OFF 만).
     * AI 자동 제어 불가 ({@link PumpOprtngType#AUTO_CAPABLE} 조합 무효 — 가변속 명령 송신 불능).
     * 마이그레이션 백필 기본값 (fail-safe).
     */
    RATED_DRIVE
}
