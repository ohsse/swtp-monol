package com.mo.swtp.instrument.domain;

/**
 * 송수펌프 조작유형 — 펌프의 물리적 설계값.
 *
 * <p>제조사 PLC 회로 결선에 따라 자동 조작 가능 펌프와 반자동 조작 가능 펌프로 분류된다.
 * 한 펌프는 두 조작유형 중 하나만 보유하며 (상호 배타), 마스터 데이터 단계에서 결정된다.</p>
 *
 * <p>시스템 상태 enum ({@code AiSystemModeCode} — {@code MANUAL}/{@code AI_AUTO}/{@code SEMI_AUTO})
 * 과 의미가 다르다: 본 enum 은 펌프 개체의 능력값 (마스터 단위), 시스템 상태 enum 은 시설의 현재
 * 운영 상태 (실시간 상태). {@code _CAPABLE} 접미사로 능력값임을 명시하여 시스템 상태와 문자열
 * 충돌을 회피한다 (펌프조작유형 ANALYZE1 안건 3). 시스템 상태 enum 의 재설계는 pump+AI 도메인
 * 백지화 사이클 1 (2026-05-12) 로 보류 — 사이클 2 {@code /dev:analyze} 에서 신규 정의 예정.</p>
 *
 * <p>도입: 펌프조작유형 ANALYZE1 안건 2·3 (2026-05-12). DB 매핑: {@code pump_m.oprtng_type_cd}
 * (DOM_CODE_20, {@code VARCHAR(20)} NOT NULL, {@code @Enumerated(EnumType.STRING)}).</p>
 */
public enum PumpOprtngType {

    /**
     * 자동 조작 가능 펌프 — AI 자동 모드·반자동 모드·수동 모드 모두 운영 가능.
     * 한국 지자체 송수펌프 실무상 표준 설치 회로 (V8_5 마이그레이션 백필 기본값).
     */
    AUTO_CAPABLE,

    /**
     * 반자동 조작 가능 펌프 — 수동·반자동 운영 가능. AI 자동 모드 ({@code ai_mode_cd = AI_AUTO}) 진입 시
     * 본 펌프는 AI 제어 대상에서 제외되어야 함 (펌프조작유형 ANALYZE1 안건 5, 인터록 강제 로직은 별도 사이클).
     */
    SEMI_AUTO_CAPABLE
}
