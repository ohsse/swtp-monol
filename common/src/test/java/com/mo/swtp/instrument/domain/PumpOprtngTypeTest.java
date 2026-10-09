package com.mo.swtp.instrument.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link PumpOprtngType} enum 단위 테스트.
 *
 * <p>펌프조작유형 ANALYZE1 (2026-05-12) 도입 — AUTO_CAPABLE/SEMI_AUTO_CAPABLE 2값 + DB VARCHAR(20)
 * 매핑 일치 검증. {@code AiSystemModeCode} 시스템 상태와의 의미·문자열 분리는 enum name 검증으로
 * 간접 보장 ({@code SEMI_AUTO_CAPABLE} 명칭에 {@code _CAPABLE} 접미사 포함, 시스템 상태 {@code SEMI_AUTO} 와 분리).</p>
 */
class PumpOprtngTypeTest {

    @Test
    void enum_은_AUTO_CAPABLE_과_SEMI_AUTO_CAPABLE_2값을_포함한다() {
        PumpOprtngType[] values = PumpOprtngType.values();

        assertThat(values).hasSize(2);
        assertThat(values).containsExactly(PumpOprtngType.AUTO_CAPABLE, PumpOprtngType.SEMI_AUTO_CAPABLE);
    }

    @Test
    void name_매핑은_DB_VARCHAR_20_값과_1대1_일치한다() {
        assertThat(PumpOprtngType.AUTO_CAPABLE.name()).isEqualTo("AUTO_CAPABLE");
        assertThat(PumpOprtngType.SEMI_AUTO_CAPABLE.name()).isEqualTo("SEMI_AUTO_CAPABLE");
    }
}
