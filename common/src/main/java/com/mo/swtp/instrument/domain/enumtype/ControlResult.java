package com.mo.swtp.instrument.domain.enumtype;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 펌프 제어결과 코드 — {@code pump_ctrl_h.ctrl_rslt} 컬럼 매핑.
 *
 * <p>송수펌프 제어이력 화면의 "제어결과" 컬럼([제어완료]/[제어취소]) 을 표현한다. 제어 요청이 최종적으로
 * 완료되었는지 취소되었는지를 분류하는 코드값이다.</p>
 *
 * <p><strong>폐기 후 재도입 (코드값 재정의)</strong> — 백지화(2026-05-12) 이전 {@code ctrl_rslt} 의
 * 구 코드값 'SUCCESS'/'WAITING'/'FAIL' 은 폐기되었고, 본 사이클에서 'COMPLETED'/'CANCELLED'
 * (제어완료/제어취소) 로 의미를 재정의하여 재도입한다 (제어이력 재도입 ANALYZE1 안건 3, 2026-06-04).
 * 화면 요구사항이 2종(완료/취소) 표출이므로 진행중(WAITING) 상태는 본 사이클 범위 밖이다.</p>
 *
 * <p>JPA 적용:</p>
 * <pre>{@code
 * @Enumerated(EnumType.STRING)
 * @Column(name = "ctrl_rslt", nullable = false, length = 20)
 * private ControlResult ctrlRslt;
 * }</pre>
 *
 * <p>DB 매핑: DOM_CODE_20, {@code VARCHAR(20)} NOT NULL.</p>
 */
@Getter
@RequiredArgsConstructor
public enum ControlResult {

    /** 제어완료 — 제어 요청이 정상 처리 완료됨 */
    COMPLETED("제어완료"),

    /** 제어취소 — 제어 요청이 취소됨 */
    CANCELLED("제어취소");

    /** 한글 표시 라벨 */
    private final String description;
}
