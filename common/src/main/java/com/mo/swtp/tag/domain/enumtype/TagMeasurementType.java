package com.mo.swtp.tag.domain.enumtype;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 태그 신호유형(Signal Type) 코드 + 단위 매핑.
 *
 * <p>{@code tag_m.tag_se_cd} 컬럼 매핑 enum. 본래 SCADA 인바운드 <b>측정 유형</b>(FRI/PRI/LEI/... INPUT 신호)
 * 약어 집합이었으나, 펌프 제어 태그({@link #CMD} — {@code io_cd='OUTPUT'} 명령 DO 신호) 편입으로
 * <b>신호유형(Signal Type)</b> 으로 의미를 확장 재해석한다 (이미 {@link #OPS} 디지털 DI 가 측정값 외 상태 신호로
 * 혼재하므로 명령 신호 추가는 자연 확장 — 제어이력 재도입 ANALYZE1 안건 5, 2026-06-04).
 * 각 신호유형은 한글 설명({@link #description})과 단위({@link #unit})를 보유하며,
 * 신호유형 ↔ 단위 1:1 고정값 정규화 결과 ({@code tag_m.unit_cd} 컬럼 폐기 후 enum 매핑으로 대체).</p>
 *
 * <p>1차 정의처: {@code .claude/rules/ot-integration.md} §3 센서 품질 관리 (결측 대체값 표).
 * 본 enum 의 값과 단위는 §3 의 결측 대체값 표와 정합하며, 향후 측정 유형이 다양화될 경우
 * (예: 초소형 펌프 L/min) 새 enum 값으로 추가한다.</p>
 *
 * <p>JPA 적용:</p>
 * <pre>{@code
 * @Enumerated(EnumType.STRING)
 * @Column(name = "tag_se_cd", nullable = false, length = 20)
 * private TagMeasurementType tagSeCd;
 * }</pre>
 *
 * <p>{@code unit} 의 빈 문자열({@code ""})은 "단위 없음"을 의미한다 (RMS 진동·OPS 가동상태).
 * DTO 응답에서는 {@code TagDto.from(Tag)} 정적 팩토리가 {@code tagSeCd.getUnit()} 매핑 결과를
 * {@code unit} 필드로 노출한다.</p>
 *
 * <p>도입 이력:</p>
 * <ul>
 *   <li>마스터도메인설계 ANALYZE1 Round 3 (2026-05-03) — {@code tag_m} 마스터 도입과 함께 5종(FRI/PRI/LEI/PWI/RMS) 등재</li>
 *   <li>태그관리 ANALYZE1 안건 6 (2026-05-08) — {@code description}+{@code unit} 필드 추가, OPS·VOI 신규 추가
 *       (송수펌프제어분석 사이클의 미결 결정 본 사이클 흡수)</li>
 *   <li>주파수측정유형등록 ANALYZE1 (2026-05-20) — {@code FQI} 신규 추가 (인버터 펌프 운전 주파수, Hz).
 *       어휘 충돌 분류 우위로 {@code SPI}(Speed Indicator) 후보 대신 {@code FQI}(Frequency Quantity Indicator) 채택.
 *       결측 대체값 정책 = Hold Last Value + 5분 한계 (VOI 동형, {@code ot-integration.md §3})</li>
 *   <li>tag_frequency_추가 ANALYZE1 (2026-05-20) — FQI 추가 (가변속 인버터 펌프 주파수 측정값).
 *       결측 대체값 정책 (Hold Last Value + 5분 초과 BAD 격상) 과 이상치 기각 절대값 기준
 *       ({@code physicalMin=0Hz}/{@code physicalMax=65Hz}) 은 {@code ot-integration.md §3} 참조.
 *       <strong>주의:</strong> 머지 커밋 13af9c3 conflict 해소 시 enum 값이 실수 누락 → tag_fqi_enum_누락 사이클 (2026-05-21) 복원</li>
 *   <li>제어이력 재도입 ANALYZE1 안건 5 (2026-06-04) — {@link #CMD} 신규 추가 (펌프 제어 태그, {@code io_cd='OUTPUT'}).
 *       enum 의미를 "측정유형 → 신호유형(Signal Type)" 으로 확장 재해석 (측정 INPUT + 명령 OUTPUT 포괄).
 *       가동상태(OPS) 와 제어(CMD) 는 별개 태그 — 송수펌프 제어이력 2·3번 섹션 제어 태그번호 표출 식별용</li>
 * </ul>
 */
@Getter
@RequiredArgsConstructor
public enum TagMeasurementType {

    /** 유량순시 (Flow Rate Indicator — 순시 유량) */
    FRI("유량순시", "m³/h"),

    /** 압력 (Pressure Indicator) */
    PRI("압력", "kgf/cm²"),

    /** 수위 (Level Indicator) */
    LEI("수위", "m"),

    /** 전력 (Power Indicator, 순시 전력) */
    PWI("전력", "kW"),

    /** 진동 (Root Mean Square — 단위 없음, 진동 RMS 값) */
    RMS("진동", ""),

    /** 펌프 가동상태 (Operation Status — boolean DI 신호, 단위 없음) */
    OPS("가동상태", ""),

    /** 밸브 개도율 (Valve Opening Indicator — 백분율) */
    VOI("개도율", "%"),

    /**
     * 주파수 (Frequency Indicator — 가변속 인버터 펌프 운전 주파수, Hz).
     *
     * <p>{@code PumpDriveType.INVERTER_DRIVE} 펌프 계측기에만 허용 — {@code RATED_DRIVE} 펌프 + 비-Pump 계측기
     * 모두 {@code TagService.validateFqiTagAllowance} 에서 차단 ({@code ot-integration.md §5 ⚠️ 절대 금지} 직결).
     * 결측 대체값 정책은 {@code ot-integration.md §3} 참조 (Hold Last Value + 5분 초과 BAD 격상, VOI 선례 동형).</p>
     */
    FQI("주파수", "Hz"),

    /**
     * 적산전력량 (Power Watt Quantity — 1분 수집 누적 미터값, kWh). 송수펌프 전력량 시계열 산정 전용.
     *
     * <p>{@link #PWI}(순시전력 kW) 와 단위·물리 성격이 분리된다 — PWI 는 순간 전력값, PWQ 는 누적 적산 미터값.
     * 전력량(kWh) 시계열은 PWQ 적산값의 버킷별 차분({@code MAX(raw_val) - MIN(raw_val)}) 으로 산정한다
     * (송수펌프가동이력_3번섹션 PLAN1 §구현 방향 3).</p>
     *
     * <p><strong>결측 대체값 정책</strong> ({@code ot-integration.md §3}): Hold Last Value <strong>미적용</strong>.
     * 적산 누적값을 직전 Good 값으로 채우면 버킷 내 MAX−MIN 차분이 0 으로 왜곡되어 전력량을 과소 산정하므로,
     * 전력량 집계는 <strong>GOOD 품질만 {@code raw_val} 차분</strong> 한다 ({@code corr_val} 미사용).
     * PWI 의 "전력 집계 오염 방지" 와 동일 결로이나 적산값 차분 특성상 HLV 가 더 치명적이다
     * (송수펌프가동이력_3번섹션 ANALYZE1 안건 4, 2026-06-02).</p>
     *
     * <p>FQI 와 달리 계측기 종류 강제 차단({@code validateFqiTagAllowance} 유사) 은 미적용 — 적산전력량은
     * 모든 펌프(인버터·정격)가 보유 가능하다 (ANALYZE1 안건 4).</p>
     */
    PWQ("적산전력량", "kWh"),

    /**
     * 운전제어 (Command — 펌프 제어 태그, 단위 없음). {@code io_cd='OUTPUT'} 명령 DO 신호.
     *
     * <p>송수펌프 제어이력 화면의 "제어대상펌프 제어 태그번호" 식별 코드값 — 펌프 가동/중지 명령을 송신하는
     * 출력 태그를 가리킨다. <strong>가동상태({@link #OPS}, INPUT) 태그와는 별개의 태그</strong>다 — 한 펌프는
     * 가동상태(OPS) 태그와 그 펌프를 제어하는 제어(CMD) 태그를 따로 보유한다 (사용자 정정, 제어이력 재도입
     * ANALYZE1 안건 5, 2026-06-04). 제어 태그 식별 기준 = {@code tag_se_cd='CMD' AND io_cd='OUTPUT'}.</p>
     *
     * <p>본 사이클은 <strong>표출 전용</strong> — 제어 태그번호를 화면에 표시하기 위한 식별값이며, 실제 제어
     * 명령 발행(OT 아웃바운드)은 재설계 사이클 결정 대기 ({@code ot-integration.md §2·§5} 보류). 측정값 시계열
     * 산정·결측 대체값 정책 대상이 아니다 (단위 없음, 명령 신호).</p>
     */
    CMD("운전제어", ""),

    /**
     * 유량적산 (Flow Rate Quantity — 누적 적산 유량, m³). 적산 유량계의 누적 미터값.
     *
     * <p>{@link #FRI}(순시 유량 m³/h) 와 단위·물리 성격이 분리된다 — FRI 는 순간 유량 rate(m³/h),
     * FRQ 는 누적 적산 volume(m³). {@link #PWI}/{@link #PWQ}(순시 전력 kW / 적산 전력량 kWh) 쌍과 동형 구조다
     * (계측기 태그 시딩 작업 신규 추가, 2026-06-10 — 유입·유출 유량계의 유량순시(FRI)·유량적산(FRQ) 쌍 등록).</p>
     */
    FRQ("유량적산", "m³");

    /** 한글 논리명 (예: "유량", "가동상태", "운전제어") */
    private final String description;

    /** 측정·신호 단위 (예: "m³/h", "%"). 빈 문자열은 "단위 없음" 을 의미 (RMS/OPS/CMD). */
    private final String unit;

    
}
