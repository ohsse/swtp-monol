package com.mo.swtp.instrument.domain.enumtype;

/**
 * 계측기 자식 종류 코드.
 *
 * <p>{@code instrument_m} 단일 마스터의 {@code equip_type_cd} DiscriminatorColumn 매핑 enum.
 * JPA {@code @Inheritance(InheritanceType.JOINED)} + {@code @DiscriminatorColumn} 패턴에서 자식 종류를 식별한다.</p>
 *
 * <p>도메인 룰: 인터록 평가({@code InterlockValidator}) 는 {@code equip_type_cd='PUMP'} 필터로
 * 액추에이터만 대상으로 평가한다. 부모 다형성 전체 조회 시 반드시 {@code equip_type_cd} 필터를 명시한다.
 * 1차 정의처: {@code .claude/rules/ot-integration.md} §Pump = Instrument 자식 정합성.</p>
 *
 * <p>마스터도메인설계 ANALYZE1 Round 2 결정 (2026-05-02).</p>
 */
public enum EquipType {

    /** 펌프 (Pump) */
    PUMP,

    /** 밸브 (Valve) */
    VALVE,

    /** 유량계 (Flow Meter) */
    FLWMTR,

    /** 압력계 (Pressure Meter) */
    PRSMTR,

    /** 수위계 (Level Meter) */
    LVMTR,

    /** 전력량계 (Electric/Power Meter) */
    ELCMTR
}
