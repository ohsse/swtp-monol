package com.mo.swtp.proc.domain.enumtype;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 운전모드 코드 — 공정/제어대상별 사용자 의도.
 *
 * <p>{@code ai_drvn_mod_p.ai_drvn_mod_cd} · {@code ai_drvn_mod_h.ai_drvn_mod_cd} 컬럼 매핑 enum.
 * 시스템 전역 설정으로 같은 공정/제어대상 화면에서 모든 사용자가 동일 모드 상태를 공유한다.</p>
 *
 * <p>도메인 룰 (송수펌프제어분석-2번섹션 ANALYZE2 안건 5, 2026-05-20):</p>
 * <ul>
 *   <li>본 enum 은 <b>사용자 의도</b> 명세 — 사용자 API 만 변경 가능</li>
 *   <li>시스템 상태 ({@code ai_mode_cd} — '0'/'1'/'2') 와는 별개 — 본 사이클은 사용자 의도만 다룸</li>
 *   <li>강제 모드 전환 정책 ({@code ot-integration.md §5} SCADA 5분 초과) 은 본 사이클 미적용 — 사이클 2 재설계 대상</li>
 * </ul>
 *
 * <p>JPA 적용:</p>
 * <pre>{@code
 * @Enumerated(EnumType.STRING)
 * @Column(name = "ai_drvn_mod_cd", nullable = false, length = 20)
 * private AiDrvnModeCode aiDrvnModCd;
 * }</pre>
 *
 * <p>DB 매핑: DOM_CODE_20, {@code VARCHAR(20)} NOT NULL.</p>
 */
@Getter
@RequiredArgsConstructor
public enum AiDrvnModeCode {

    /** AI — 운전 자동 제어 모드 (AI 자동 운전 실행) */
    AI("AI"),

    /** AI 추천 — AI 추천값 표시, 운전 명령 미송신 (운영자 수락 절차) */
    AI_RECOMD("AI추천"),

    /** AI 분석 — 분석 모드, 제어 명령 억제 (이력 분석 전용) */
    AI_ANLS("AI분석");

    /** 한글 표시 라벨 */
    private final String description;
}
