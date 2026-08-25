package com.gary.bilibili.common.reliability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Repository
@ConditionalOnProperty(name = "spring.datasource.url")
public class FailedMessageRepository {

    private static final RowMapper<FailedMessage> ROW_MAPPER = (resultSet, rowNum) -> new FailedMessage(
            resultSet.getLong("id"), resultSet.getString("topic"),
            resultSet.getString("consumer_group"), resultSet.getString("message_key"),
            resultSet.getString("payload_type"), resultSet.getString("payload"),
            resultSet.getString("exception_type"), resultSet.getString("error_message"),
            resultSet.getString("trace_id"), resultSet.getInt("attempt_count"),
            resultSet.getString("status"), resultSet.getInt("replay_count"),
            toLocalDateTime(resultSet.getTimestamp("create_time")),
            toLocalDateTime(resultSet.getTimestamp("update_time")),
            toLocalDateTime(resultSet.getTimestamp("last_replay_time")));

    private final JdbcTemplate jdbcTemplate;

    public FailedMessageRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void saveFailure(String topic, String consumerGroup, String messageKey,
                            String payloadType, String payload, Throwable failure,
                            String traceId, int attempts) {
        String fingerprint = fingerprint(topic, consumerGroup, messageKey, payload);
        jdbcTemplate.update("""
                INSERT INTO mq_failed_message
                    (fingerprint, topic, consumer_group, message_key, payload_type, payload,
                     exception_type, error_message, trace_id, attempt_count, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'FAILED')
                ON DUPLICATE KEY UPDATE
                    exception_type = VALUES(exception_type), error_message = VALUES(error_message),
                    trace_id = VALUES(trace_id), attempt_count = attempt_count + VALUES(attempt_count),
                    status = 'FAILED', update_time = CURRENT_TIMESTAMP
                """, fingerprint, topic, consumerGroup, messageKey, payloadType, payload,
                failure.getClass().getName(), safeMessage(failure), traceId, attempts);
    }

    public Optional<FailedMessage> findById(long id) {
        return jdbcTemplate.query("SELECT * FROM mq_failed_message WHERE id = ?", ROW_MAPPER, id)
                .stream().findFirst();
    }

    public List<FailedMessage> findFailures(String topic, String status, int offset, int size) {
        return jdbcTemplate.query("""
                SELECT * FROM mq_failed_message
                WHERE (? = '' OR topic = ?) AND status = ?
                ORDER BY update_time DESC, id DESC LIMIT ? OFFSET ?
                """, ROW_MAPPER, topic, topic, status, size, offset);
    }

    public long countFailures(String topic, String status) {
        Long count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM mq_failed_message
                WHERE (? = '' OR topic = ?) AND status = ?
                """, Long.class, topic, topic, status);
        return count == null ? 0 : count;
    }

    public long countPending() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM mq_failed_message WHERE status = 'FAILED'", Long.class);
        return count == null ? 0 : count;
    }

    public boolean markReplayed(long id) {
        return jdbcTemplate.update("""
                UPDATE mq_failed_message SET status = 'REPLAYED', replay_count = replay_count + 1,
                    last_replay_time = CURRENT_TIMESTAMP, update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND status = 'FAILED'
                """, id) == 1;
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    private String fingerprint(String topic, String group, String key, String payload) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((topic + '\n' + group + '\n' + key + '\n' + payload)
                            .getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot fingerprint failed message", exception);
        }
    }

    private String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        String value = message == null || message.isBlank()
                ? failure.getClass().getSimpleName() : message;
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }
}
