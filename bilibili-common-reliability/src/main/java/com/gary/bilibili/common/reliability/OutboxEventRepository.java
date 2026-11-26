package com.gary.bilibili.common.reliability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "spring.datasource.url")
public class OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public OutboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insert(String eventId, String owner, String aggregateType,
                          String aggregateId, String eventType, String topic,
                          String payloadType, String payload) {
        if (isArchived(eventId)) return false;
        try {
            int inserted = jdbcTemplate.update("""
                    insert into reliable_event_outbox
                        (event_id, owner, aggregate_type, aggregate_id, event_type,
                         topic, payload_type, payload)
                    values (?, ?, ?, ?, ?, ?, ?, cast(? as json))
                    """, eventId, owner, aggregateType, aggregateId, eventType,
                    topic, payloadType, payload);
            if (isArchived(eventId)) {
                jdbcTemplate.update("delete from reliable_event_outbox where event_id = ? and status = 'PENDING'", eventId);
                return false;
            }
            return inserted == 1;
        } catch (DuplicateKeyException duplicate) {
            return false;
        }
    }

    private boolean isArchived(String eventId) {
        return !jdbcTemplate.query("select id from reliable_event_outbox_archive where event_id = ? for share",
                (row, index) -> row.getLong(1), eventId).isEmpty();
    }

    public int exhaust(String owner, int maxAttempts, int reclaimSeconds, int batchSize) {
        return jdbcTemplate.update("""
                update reliable_event_outbox set status = 'EXHAUSTED', claim_token = null, locked_until = null
                where owner = ? and attempt_count >= ? and (
                    status in ('FAILED', 'PENDING') or (status = 'PROCESSING' and
                    (locked_until <= now(3) or (locked_until is null
                        and locked_at < timestampadd(SECOND, -?, now(3))))))
                order by id limit ?
                """, owner, maxAttempts, reclaimSeconds, batchSize);
    }

    public long countExhausted(String owner) {
        Long value = jdbcTemplate.queryForObject("""
                select count(*) from reliable_event_outbox where owner = ? and status = 'EXHAUSTED'
                """, Long.class, owner);
        return value == null ? 0L : value;
    }

    public List<OutboxEvent> findDispatchable(String owner, int limit, int reclaimSeconds,
                                               int maxAttempts) {
        return jdbcTemplate.query("""
                select id, event_id, owner, aggregate_type, aggregate_id, event_type,
                       topic, payload_type, cast(payload as char), attempt_count, create_time
                from reliable_event_outbox
                where owner = ? and attempt_count < ? and (
                    (status in ('PENDING', 'FAILED') and available_at <= now(3))
                    or (status = 'PROCESSING'
                        and (locked_until <= now(3) or (locked_until is null
                            and locked_at < date_sub(now(3), interval ? second)))))
                order by id asc limit ?
                """, (rs, rowNum) -> new OutboxEvent(
                rs.getLong("id"), rs.getString("event_id"), rs.getString("owner"),
                rs.getString("aggregate_type"), rs.getString("aggregate_id"),
                rs.getString("event_type"), rs.getString("topic"),
                rs.getString("payload_type"), rs.getString(9),
                rs.getInt("attempt_count"),
                rs.getTimestamp("create_time").toLocalDateTime()),
                owner, maxAttempts, reclaimSeconds, limit);
    }

    public Optional<OutboxEventLease> claim(long id, int reclaimSeconds, int maxAttempts) {
        String token = UUID.randomUUID().toString();
        int updated = jdbcTemplate.update("""
                update reliable_event_outbox
                set status = 'PROCESSING', locked_at = now(3),
                    attempt_count = attempt_count + 1, last_error = null,
                    claim_token = ?, locked_until = timestampadd(SECOND, ?, now(3))
                where id = ? and attempt_count < ? and (
                    (status in ('PENDING', 'FAILED') and available_at <= now(3))
                    or (status = 'PROCESSING'
                        and (locked_until <= now(3) or (locked_until is null
                            and locked_at < date_sub(now(3), interval ? second)))))
                """, token, reclaimSeconds, id, maxAttempts, reclaimSeconds);
        return updated == 1 ? Optional.of(new OutboxEventLease(id, token)) : Optional.empty();
    }

    public boolean markPublished(OutboxEventLease lease) {
        return jdbcTemplate.update("""
                update reliable_event_outbox
                set status = 'PUBLISHED', published_at = now(3), locked_at = null,
                    last_error = null, claim_token = null, locked_until = null
                where id = ? and status = 'PROCESSING' and claim_token = ? and locked_until > now(3)
                """, lease.id(), lease.token()) == 1;
    }

    public boolean markFailed(OutboxEventLease lease, String error, LocalDateTime availableAt) {
        return jdbcTemplate.update("""
                update reliable_event_outbox
                set status = 'FAILED', available_at = ?, locked_at = null,
                    last_error = ?, claim_token = null, locked_until = null
                where id = ? and status = 'PROCESSING' and claim_token = ? and locked_until > now(3)
                """, availableAt, truncate(error), lease.id(), lease.token()) == 1;
    }

    public long countPending(String owner) {
        Long value = jdbcTemplate.queryForObject("""
                select count(*) from reliable_event_outbox
                where owner = ? and status <> 'PUBLISHED'
                """, Long.class, owner);
        return value == null ? 0L : value;
    }

    private String truncate(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown dispatch failure";
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
