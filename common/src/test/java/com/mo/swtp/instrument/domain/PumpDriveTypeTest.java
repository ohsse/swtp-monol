package com.mo.swtp.instrument.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/**
 * {@link PumpDriveType} enum 단위 테스트.
 *
 * <p>pump_drive_type ANALYZE1 (2026-05-20) 도입 enum — 값 2건 정의 + {@code name()} 직렬화 일치 확인.
 * DB 매핑 ({@code pump_m.drive_type_cd} VARCHAR(20) — {@code @Enumerated(EnumType.STRING)}) 의
 * 직렬화 안정성을 보장한다.</p>
 */
class PumpDriveTypeTest {

    @Test
    void values_는_INVERTER_DRIVE_와_RATED_DRIVE_2건을_정의한다() {
        assertThat(PumpDriveType.values()).containsExactly(
                PumpDriveType.INVERTER_DRIVE,
                PumpDriveType.RATED_DRIVE);
    }

    @Test
    void INVERTER_DRIVE_의_name_은_INVERTER_DRIVE_문자열과_일치한다() {
        assertThat(PumpDriveType.INVERTER_DRIVE.name()).isEqualTo("INVERTER_DRIVE");
    }

    @Test
    void RATED_DRIVE_의_name_은_RATED_DRIVE_문자열과_일치한다() {
        assertThat(PumpDriveType.RATED_DRIVE.name()).isEqualTo("RATED_DRIVE");
    }

    @Test
    void valueOf_는_DB_문자열로부터_enum_을_역직렬화한다() {
        assertThat(PumpDriveType.valueOf("INVERTER_DRIVE")).isEqualTo(PumpDriveType.INVERTER_DRIVE);
        assertThat(PumpDriveType.valueOf("RATED_DRIVE")).isEqualTo(PumpDriveType.RATED_DRIVE);
    }
}
