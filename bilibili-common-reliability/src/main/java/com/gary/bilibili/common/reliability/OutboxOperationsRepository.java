package com.gary.bilibili.common.reliability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@ConditionalOnProperty(name = "spring.datasource.url")
public class OutboxOperationsRepository {
    private final JdbcTemplate jdbc;

    public OutboxOperationsRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public List<OutboxSummary> find(boolean archived, long afterId, int size) {
        String table = archived ? "reliable_event_outbox_archive" : "reliable_event_outbox";
        String state = archived ? "PUBLISHED" : "EXHAUSTED";
        return jdbc.query("""
                select id, event_id, owner, topic, status, attempt_count, replay_count, last_error,
                       create_time, published_at from %s where status = ? and id > ? order by id limit ?
                """.formatted(table), (row, index) -> new OutboxSummary(row.getLong(1), row.getString(2),
                row.getString(3), row.getString(4), row.getString(5), row.getInt(6), row.getInt(7),
                row.getString(8), row.getObject(9, LocalDateTime.class), row.getObject(10, LocalDateTime.class)),
                state, Math.max(0, afterId), Math.clamp(size, 1, 100));
    }

    public List<ReplayAudit> audit(String sourceType, long sourceId) {
        return jdbc.query("""
                select request_id, reason, event_id, expected_replay_count, created_at from mq_replay_audit
                where source_type = ? and source_id = ? order by created_at desc, request_id limit 100
                """, (row, index) -> new ReplayAudit(row.getString(1), row.getString(2), row.getString(3),
                row.getInt(4), row.getObject(5, LocalDateTime.class)), sourceType, sourceId);
    }

    public record OutboxSummary(long id, String eventId, String owner, String topic, String status,
                                int attemptCount, int replayCount, String lastError,
                                LocalDateTime createdAt, LocalDateTime publishedAt) { }
    public record ReplayAudit(String requestId, String reason, String eventId, int expectedReplayCount,
                              LocalDateTime createdAt) { }
}
