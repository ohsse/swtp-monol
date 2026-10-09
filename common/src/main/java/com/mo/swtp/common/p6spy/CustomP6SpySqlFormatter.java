package com.mo.swtp.common.p6spy;

import com.p6spy.engine.spy.appender.MessageFormattingStrategy;
import org.hibernate.engine.jdbc.internal.BasicFormatterImpl;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * P6Spy SQL 로그 커스텀 포매터.
 *
 * <p>바인딩된 파라미터가 치환된 완전한 SQL을 Hibernate {@link BasicFormatterImpl}로
 * pretty-print하여 가독성을 높인다. JPA(Hibernate)와 MyBatis 쿼리 모두 이 포매터를
 * 거쳐 출력된다.</p>
 *
 * <p>출력 형식:</p>
 * <pre>
 * 2026-04-16 10:30:00 | 12ms | statement | connection 1
 *     select
 *         u.user_id,
 *         u.user_nm
 *     from
 *         user_m u
 *     where
 *         u.user_id = 'admin'
 * </pre>
 */
public class CustomP6SpySqlFormatter implements MessageFormattingStrategy {

    /** 로그 출력 시각 포맷 */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Hibernate SQL pretty-print 포매터 */
    private static final BasicFormatterImpl SQL_FORMATTER = new BasicFormatterImpl();

    /**
     * P6Spy가 SQL 실행 시 호출하는 포매팅 메서드.
     *
     * @param connectionId 커넥션 ID
     * @param now          실행 시각 (epoch milliseconds 문자열)
     * @param elapsed      실행 시간 (ms)
     * @param category     SQL 카테고리 (statement, commit, rollback 등)
     * @param prepared     바인딩 파라미터 치환 전 SQL
     * @param sql          바인딩 파라미터가 치환된 완전한 SQL
     * @param url          JDBC URL
     * @return 포매팅된 로그 문자열
     */
    @Override
    public String formatMessage(int connectionId, String now, long elapsed,
                                String category, String prepared, String sql, String url) {
        if (sql == null || sql.isBlank()) {
            return "";
        }

        // p6spy의 commit/rollback 카테고리에서 now가 빈 문자열로 전달될 수 있으므로 폴백 처리
        LocalDateTime timestamp;
        try {
            timestamp = LocalDateTime.ofInstant(
                    Instant.ofEpochMilli(Long.parseLong(now)), ZoneId.systemDefault());
        } catch (NumberFormatException e) {
            timestamp = LocalDateTime.now();
        }

        return String.format(
                "%s | %dms | %s | connection %d%n%s",
                TIME_FORMATTER.format(timestamp),
                elapsed,
                category,
                connectionId,
                SQL_FORMATTER.format(sql)
        );
    }
}
