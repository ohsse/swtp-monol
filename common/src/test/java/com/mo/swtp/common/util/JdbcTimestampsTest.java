package com.mo.swtp.common.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

/**
 * {@link JdbcTimestamps} 단위 테스트.
 *
 * <p>native_timestamp_cast_fix (2026-06-05) — Hibernate 6+ native query 가 {@code timestamp without
 * time zone} 컬럼을 {@link LocalDateTime} 으로 반환하는 케이스에서 {@code (java.sql.Timestamp)} 직접
 * 캐스팅이 {@link ClassCastException} 을 일으키는 버그를 재현·검증한다. {@code LocalDateTime_입력은_그대로_반환된다}
 * 가 버그 재현 케이스 (수정 전 RED, 안전 변환 후 GREEN).</p>
 */
class JdbcTimestampsTest {

    private static final LocalDateTime SAMPLE = LocalDateTime.of(2026, 6, 5, 11, 42, 42);

    @Test
    void LocalDateTime_입력은_그대로_반환된다() {
        // Hibernate 6+ native query 가 timestamp 컬럼을 LocalDateTime 으로 반환하는 케이스 (버그 재현 대상)
        Object nativeResult = SAMPLE;

        assertThat(JdbcTimestamps.toLocalDateTime(nativeResult)).isEqualTo(SAMPLE);
    }

    @Test
    void Timestamp_입력은_LocalDateTime_으로_변환된다() {
        Object nativeResult = Timestamp.valueOf(SAMPLE);

        assertThat(JdbcTimestamps.toLocalDateTime(nativeResult)).isEqualTo(SAMPLE);
    }

    @Test
    void OffsetDateTime_입력은_LocalDateTime_으로_변환된다() {
        Object nativeResult = OffsetDateTime.of(SAMPLE, ZoneOffset.UTC);

        assertThat(JdbcTimestamps.toLocalDateTime(nativeResult)).isEqualTo(SAMPLE);
    }

    @Test
    void null_입력은_null_을_반환한다() {
        assertThat(JdbcTimestamps.toLocalDateTime(null)).isNull();
    }

    @Test
    void 지원하지_않는_타입은_IllegalStateException_을_던진다() {
        assertThatThrownBy(() -> JdbcTimestamps.toLocalDateTime("2026-06-05"))
                .isInstanceOf(IllegalStateException.class);
    }
}
