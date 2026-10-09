package com.mo.swtp.common.util;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

/**
 * native query scalar 결과의 timestamp 슬롯을 {@link LocalDateTime} 으로 안전 변환하는 유틸.
 *
 * <p>Hibernate 6+ 환경에서 {@link jakarta.persistence.EntityManager#createNativeQuery(String)} 의
 * scalar 결과로 PostgreSQL {@code timestamp without time zone} 컬럼이 {@link java.sql.Timestamp} 가
 * 아니라 {@link LocalDateTime} 으로 반환된다. 과거 코드처럼 {@code (java.sql.Timestamp)} 로 직접
 * 캐스팅하면 {@link ClassCastException} 이 발생하므로, 드라이버·Hibernate 버전 차이에 견고하도록 본
 * 유틸로 변환한다 (native_timestamp_cast_fix, 2026-06-05).</p>
 */
public final class JdbcTimestamps {

    private JdbcTimestamps() {
    }

    /**
     * native query 결과 슬롯을 {@link LocalDateTime} 으로 변환한다.
     *
     * @param value native query {@code Object[]} 의 timestamp 슬롯 값
     * @return 변환된 {@link LocalDateTime} (입력이 {@code null} 이면 {@code null})
     * @throws IllegalStateException 지원하지 않는 타입인 경우
     */
    public static LocalDateTime toLocalDateTime(Object value) {
        return switch (value) {
            case null -> null;
            case LocalDateTime localDateTime -> localDateTime;
            case Timestamp timestamp -> timestamp.toLocalDateTime();
            case OffsetDateTime offsetDateTime -> offsetDateTime.toLocalDateTime();
            default -> throw new IllegalStateException(
                    "지원하지 않는 timestamp 타입: " + value.getClass().getName());
        };
    }
}
