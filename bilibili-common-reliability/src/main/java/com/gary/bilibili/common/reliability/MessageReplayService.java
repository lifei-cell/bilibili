package com.gary.bilibili.common.reliability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

/** Replay only schedules durable dispatch; no MQ network call is made inside a database transaction. */
@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class MessageReplayService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final MessageLifecyclePolicy policy;
    private final String owner;
    private final String producerGroup;

    public MessageReplayService(JdbcTemplate jdbc, PlatformTransactionManager manager,
                                MessageLifecyclePolicy policy,
                                @Value("${spring.application.name}") String owner,
                                @Value("${rocketmq.producer.group:}") String producerGroup) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
        this.policy = policy;
        this.owner = owner;
        this.producerGroup = producerGroup;
    }

    public ReplayReceipt replayOutbox(long id, MessageReplayRequest request) {
        return replay("OUTBOX", id, request);
    }

    public ReplayReceipt replayFailure(long id, MessageReplayRequest request) {
        if (producerGroup.isBlank()) throw new IllegalStateException("This service has no Outbox publisher");
        return replay("DLQ", id, request);
    }

    private ReplayReceipt replay(String type, long id, MessageReplayRequest request) {
        request.validate();
        if (id < 1) throw new IllegalArgumentException("Invalid message id");
        return transactions.execute(tx -> {
            // The source lock serializes concurrent operators; expectedReplayCount prevents old
            // requests from replaying a later failure cycle. Audit and state commit atomically.
            String table = "OUTBOX".equals(type) ? "reliable_event_outbox" : "mq_failed_message";
            var rows = jdbc.query("select status, replay_count, "
                            + "(last_replay_time is null or last_replay_time <= timestampadd(SECOND, -?, now(3))) as cooled "
                            + "from " + table + " where id = ? for update",
                    (row, index) -> new State(row.getString(1), row.getInt(2), row.getBoolean(3)),
                    policy.replayCooldownSeconds(), id);
            List<ReplayReceipt> prior = jdbc.query("""
                    select request_id, source_type, source_id, expected_replay_count, reason, event_id
                    from mq_replay_audit where request_id = ? for share
                    """, (row, index) -> {
                if (!type.equals(row.getString(2)) || id != row.getLong(3)
                        || request.expectedReplayCount() != row.getInt(4) || !request.reason().equals(row.getString(5))) {
                    throw new IllegalArgumentException("Replay requestId was already used for a different request");
                }
                return new ReplayReceipt(row.getString(1), type, id, row.getString(6));
            }, request.requestId());
            if (!prior.isEmpty()) return prior.getFirst();
            if (rows.isEmpty()) throw new IllegalArgumentException("Message not found or already archived");
            State state = rows.getFirst();
            String required = "OUTBOX".equals(type) ? "EXHAUSTED" : "FAILED";
            if (!required.equals(state.status()) || state.replayCount() != request.expectedReplayCount()
                    || state.replayCount() >= policy.maxReplays() || !state.cooled()) {
                throw new IllegalStateException("Replay rejected: state, version, cooldown or replay limit");
            }
            String eventId;
            if ("OUTBOX".equals(type)) {
                eventId = jdbc.queryForObject("select event_id from reliable_event_outbox where id = ?", String.class, id);
                jdbc.update("""
                        update reliable_event_outbox set status = 'PENDING', attempt_count = 0,
                        available_at = now(3), locked_at = null, locked_until = null, claim_token = null,
                        replay_count = replay_count + 1, last_replay_time = now(3) where id = ?
                        """, id);
            } else {
                eventId = "dlq-replay-" + id + "-" + (state.replayCount() + 1);
                jdbc.update("""
                        insert into reliable_event_outbox
                        (event_id, owner, aggregate_type, aggregate_id, event_type, topic, payload_type, payload)
                        select ?, ?, 'mq-failure', cast(id as char), 'DLQ_REPLAY', topic, payload_type, payload
                        from mq_failed_message where id = ?
                        """, eventId, owner, id);
                jdbc.update("""
                        update mq_failed_message set status = 'REPLAYED', replay_count = replay_count + 1,
                        last_replay_time = now(3) where id = ?
                        """, id);
            }
            jdbc.update("""
                    insert into mq_replay_audit
                    (request_id, source_type, source_id, expected_replay_count, reason, event_id)
                    values (?, ?, ?, ?, ?, ?)
                    """, request.requestId(), type, id, request.expectedReplayCount(), request.reason(), eventId);
            return new ReplayReceipt(request.requestId(), type, id, eventId);
        });
    }

    private record State(String status, int replayCount, boolean cooled) { }
    public record ReplayReceipt(String requestId, String sourceType, long sourceId, String eventId) { }
}
