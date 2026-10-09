package com.mo.swtp.tag.domain.enumtype;

/**
 * 태그 입출력 구분 코드.
 *
 * <p>{@code tag_m.io_cd} 컬럼 매핑 enum. SCADA 태그가 백엔드 → SCADA(출력),
 * SCADA → 백엔드(입력), 또는 양방향(BIDIR) 흐름인지 구분한다.</p>
 *
 * <p>JPA 적용:</p>
 * <pre>{@code
 * @Enumerated(EnumType.STRING)
 * @Column(name = "io_cd", nullable = false, length = 20)
 * private IoCode ioCd;
 * }</pre>
 *
 * <p>마스터도메인설계 ANALYZE1 Round 3 + PLAN1 사용자 결정 (2026-05-03) —
 * {@code io_yn} (단순 boolean) vs {@code io_cd} (3값 enum) 중 후자 채택.
 * 양방향 태그 표현 가능, 향후 확장 여지 (ex. WRITE_ONCE).</p>
 */
public enum IoCode {

    /** SCADA → 백엔드 입력 태그 (수신 전용) */
    INPUT,

    /** 백엔드 → SCADA 출력 태그 (송신 전용) */
    OUTPUT,

    /** 양방향 태그 (수신·송신 모두) */
    BIDIR
}
