package com.mo.swtp.common.enumtype;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 시계열 조회 단위 (시/일/월/년).
 *
 * <p>송수펌프 가동이력 대시보드 1번섹션 셀렉트박스 {@code [시/일/월/년]} 와 1:1 매핑되는 조회 단위 enum 이다.
 * 선택값에 따라 시계열 차트(3번섹션)의 x축 버킷 granularity 가 시간/일/월/년 단위로 변동한다
 * (송수펌프가동이력_3번섹션 PLAN1 §구현 방향 1).</p>
 *
 * <p>각 enum 값은 PostgreSQL {@code date_trunc(text, timestamp)} 함수의 첫 인자({@link #dateTruncUnit})를
 * 내부 고정 매핑으로 보유한다. 외부 문자열을 집계 쿼리에 직접 주입하지 않고 본 enum 의 4개 값으로만 제약하여
 * SQL 인젝션 표면을 축소한다 (PLAN1 §구현 방향 1 — backend-engineer 안건 3 권고).</p>
 *
 * <p>위치: {@code com.mo.swtp.common.enumtype} (범용 조회 단위 — 특정 도메인 비종속). {@link YnType} 동일 패키지 선례.</p>
 *
 * <p>DTO 에서는 {@code @Schema(description = "...", implementation = InqUnit.class)} 만 명시하고
 * {@code allowableValues} · {@code @Pattern} · {@code example} 은 중복 작성하지 않는다.
 * Swagger/OpenAPI 가 enum 허용값을 자동으로 노출한다.</p>
 *
 * <p>도입: 송수펌프가동이력_3번섹션 ANALYZE1 (2026-06-02).</p>
 */
@Getter
@RequiredArgsConstructor
public enum InqUnit {

    /** 시간 단위 (1시간 버킷). */
    HOUR("hour"),

    /** 일 단위 (1일 버킷). */
    DAY("day"),

    /** 월 단위 (1개월 버킷). */
    MONTH("month"),

    /** 년 단위 (1년 버킷). */
    YEAR("year");

    /** PostgreSQL {@code date_trunc} 함수 첫 인자 (예: "hour"). 집계 쿼리 text 바인드 파라미터로 사용. */
    private final String dateTruncUnit;
}
