package com.mo.swtp.tag.domain.enumtype;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link TagMeasurementType} enum 단위 테스트 — 단위 매핑 정합성 검증.
 *
 * <p>본 사이클(태그관리 ANALYZE1 안건 6, 2026-05-08)에서 도입된 description+unit 필드 매핑이
 * {@code .claude/rules/ot-integration.md} §3 결측 대체값 표와 정합한지 확인한다.
 * {@code tag_m.unit_cd} 컬럼 폐기 후 본 enum 매핑이 측정 단위 SSOT 가 되었으므로
 * 단위 매핑이 변경되면 본 테스트가 실패한다.</p>
 */
class TagMeasurementTypeTest {

    @Test
    void 측정_유형별_단위가_고정값으로_매핑된다() {
        assertThat(TagMeasurementType.FRI.getUnit()).isEqualTo("m³/h");
        assertThat(TagMeasurementType.PRI.getUnit()).isEqualTo("kgf/cm²");
        assertThat(TagMeasurementType.LEI.getUnit()).isEqualTo("m");
        assertThat(TagMeasurementType.PWI.getUnit()).isEqualTo("kW");
        assertThat(TagMeasurementType.RMS.getUnit()).isEqualTo("");
        assertThat(TagMeasurementType.OPS.getUnit()).isEqualTo("");
        assertThat(TagMeasurementType.VOI.getUnit()).isEqualTo("%");
        assertThat(TagMeasurementType.FQI.getUnit()).isEqualTo("Hz");
        assertThat(TagMeasurementType.PWQ.getUnit()).isEqualTo("kWh");
        assertThat(TagMeasurementType.CMD.getUnit()).isEqualTo("");
        assertThat(TagMeasurementType.FRQ.getUnit()).isEqualTo("m³");
    }

    @Test
    void 측정_유형별_한글_설명이_매핑된다() {
        assertThat(TagMeasurementType.FRI.getDescription()).isEqualTo("유량순시");
        assertThat(TagMeasurementType.PRI.getDescription()).isEqualTo("압력");
        assertThat(TagMeasurementType.LEI.getDescription()).isEqualTo("수위");
        assertThat(TagMeasurementType.PWI.getDescription()).isEqualTo("전력");
        assertThat(TagMeasurementType.RMS.getDescription()).isEqualTo("진동");
        assertThat(TagMeasurementType.OPS.getDescription()).isEqualTo("가동상태");
        assertThat(TagMeasurementType.VOI.getDescription()).isEqualTo("개도율");
        assertThat(TagMeasurementType.FQI.getDescription()).isEqualTo("주파수");
        assertThat(TagMeasurementType.PWQ.getDescription()).isEqualTo("적산전력량");
        assertThat(TagMeasurementType.CMD.getDescription()).isEqualTo("운전제어");
        assertThat(TagMeasurementType.FRQ.getDescription()).isEqualTo("유량적산");
    }

    @Test
    void enum_값은_총_11종이다() {
        assertThat(TagMeasurementType.values()).hasSize(11);
    }

    @Test
    void OPS_와_RMS_는_단위_없음을_빈_문자열로_표현한다() {
        // 빈 문자열 = "단위 없음" 표현 — Optional 대신 채택 (PLAN1 §구현 방향, 안건 6 wtp-backend-engineer 권고)
        assertThat(TagMeasurementType.OPS.getUnit()).isEmpty();
        assertThat(TagMeasurementType.RMS.getUnit()).isEmpty();
    }

    @Test
    void FQI_주파수_단위는_Hz다() {
        // 주파수측정유형등록 ANALYZE1 (2026-05-20) — 인버터 펌프 운전 주파수 측정 유형
        assertThat(TagMeasurementType.FQI.getDescription()).isEqualTo("주파수");
        assertThat(TagMeasurementType.FQI.getUnit()).isEqualTo("Hz");
    }

    @Test
    void PWQ_적산전력량_단위는_kWh다() {
        // 송수펌프가동이력_3번섹션 ANALYZE1 (2026-06-02) — 송수펌프 전력량 시계열 산정용 적산 미터값.
        // PWI(순시전력 kW)와 단위·물리 성격 분리 (ot-integration.md §3 PWQ 결측 정책 — HLV 미적용)
        assertThat(TagMeasurementType.PWQ.getDescription()).isEqualTo("적산전력량");
        assertThat(TagMeasurementType.PWQ.getUnit()).isEqualTo("kWh");
    }

    @Test
    void CMD_운전제어는_단위_없는_OUTPUT_명령_신호다() {
        // 제어이력 재도입 ANALYZE1 안건 5 (2026-06-04) — 펌프 제어 태그(io_cd='OUTPUT') 식별 코드값.
        // enum 의미 "측정유형 → 신호유형(Signal Type)" 확장 재해석. 가동상태(OPS)와 별개 태그.
        assertThat(TagMeasurementType.CMD.getDescription()).isEqualTo("운전제어");
        assertThat(TagMeasurementType.CMD.getUnit()).isEmpty();
    }

    @Test
    void FRQ_유량적산_단위는_세제곱미터다() {
        // 계측기 태그 시딩 (2026-06-10) — 유입·유출 유량계의 유량적산 측정 유형.
        // FRI(순시 유량 m³/h) 와 단위·물리 성격 분리 (PWI/PWQ 쌍 동형).
        assertThat(TagMeasurementType.FRQ.getDescription()).isEqualTo("유량적산");
        assertThat(TagMeasurementType.FRQ.getUnit()).isEqualTo("m³");
    }
}
