package com.mo.swtp.instrument.domain.enumtype;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 펌프 제어요청구분 코드 — {@code pump_ctrl_h.ctrl_div} 컬럼 매핑.
 *
 * <p>송수펌프 제어이력 화면의 "제어요청구분" 컬럼([가동]/[중지]) 을 표현한다. 제어 명령의 의도
 * (펌프 가동 요청 / 펌프 중지 요청) 를 분류하는 코드값이다.</p>
 *
 * <p><strong>폐기 후 재도입 (코드값 재정의)</strong> — 백지화(2026-05-12) 이전 {@code ctrl_div} 의
 * 구 코드값 'MANUAL'/'AUTO' 는 폐기되었고, 본 사이클에서 'START'/'STOP' (가동/중지) 로 의미를 재정의하여
 * 재도입한다 (제어이력 재도입 ANALYZE1 안건 3, 2026-06-04). 본 사이클은 조회 전용이므로 제어 명령 발행
 * 경로는 없다 — 과거 이력 표출용 분류값이다.</p>
 *
 * <p>JPA 적용:</p>
 * <pre>{@code
 * @Enumerated(EnumType.STRING)
 * @Column(name = "ctrl_div", nullable = false, length = 20)
 * private ControlCommand ctrlDiv;
 * }</pre>
 *
 * <p>DB 매핑: DOM_CODE_20, {@code VARCHAR(20)} NOT NULL.</p>
 */
@Getter
@RequiredArgsConstructor
public enum ControlCommand {

    /** 가동 — 펌프 가동(기동) 요청 */
    START("가동"),

    /** 중지 — 펌프 중지(정지) 요청 */
    STOP("중지");

    /** 한글 표시 라벨 */
    private final String description;
}
