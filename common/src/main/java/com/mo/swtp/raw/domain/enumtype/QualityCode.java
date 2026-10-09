package com.mo.swtp.raw.domain.enumtype;

/**
 * SCADA 측정 품질 코드.
 *
 * <p>{@code rawdata_1m_h.quality_cd} 컬럼 매핑 enum. SCADA 인바운드 데이터의
 * QUALITY 필드를 3단계로 처리한다. 1차 정의처: {@code .claude/rules/ot-integration.md} §3 센서 품질 관리.</p>
 *
 * <p>처리 정책:</p>
 * <ul>
 *   <li>{@link #GOOD} — 정상 측정값, 그대로 저장</li>
 *   <li>{@link #BAD} — 센서·통신 장애, 결측 처리 + 대체값 적용</li>
 *   <li>{@link #UNCERTAIN} — 신뢰도 낮음 (전환 중·범위 경계), 로그 기록 후 저장 (집계 시 가중치 0.5)</li>
 * </ul>
 *
 * <p>JPA 적용:</p>
 * <pre>{@code
 * @Enumerated(EnumType.STRING)
 * @Column(name = "quality_cd", nullable = false, length = 20)
 * private QualityCode qualityCd;
 * }</pre>
 *
 * <p>마스터도메인설계 ANALYZE1 Round 3 결정 (2026-05-03) — {@code rawdata_1m_h} 마스터 도입에 따라 등재.</p>
 */
public enum QualityCode {

    /** 정상 측정값 (SCADA QUALITY 0) */
    GOOD,

    /** 센서·통신 장애 (SCADA QUALITY 1) */
    BAD,

    /** 신뢰도 낮음 — 전환 중·범위 경계 (SCADA QUALITY 2) */
    UNCERTAIN
}
