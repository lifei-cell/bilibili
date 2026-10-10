package com.gary.bilibili.common.reliability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;
import java.util.UUID;

@Repository
@ConditionalOnProperty(name = "spring.datasource.url")
public class MessageInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<MessageInboxLease> acquire(String topic, String consumerGroup, String messageKey,
                                               int leaseSeconds) {
        String token = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    insert into mq_consumed_message
                        (topic, consumer_group, message_key, status, claim_generation, claim_token, locked_until)
                    values (?, ?, ?, 'PROCESSING', 1, ?, timestampadd(SECOND, ?, now(3)))
                    """, topic, consumerGroup, messageKey, token, leaseSeconds);
            return Optional.of(new MessageInboxLease(topic, consumerGroup, messageKey, 1, token));
        } catch (DuplicateKeyException duplicate) {
            int updated = jdbcTemplate.update("""
                    update mq_consumed_message
                    set status = 'PROCESSING', attempt_count = attempt_count + 1,
                        claim_generation = claim_generation + 1, claim_token = ?,
                        locked_until = timestampadd(SECOND, ?, now(3)), last_error = null
                    where topic = ? and consumer_group = ? and message_key = ?
                      and (status = 'FAILED'
                           or (status = 'PROCESSING' and (locked_until is null or locked_until <= now(3))))
                    """, token, leaseSeconds, topic, consumerGroup, messageKey);
            if (updated == 0) {
                return Optional.empty();
            }
            return jdbcTemplate.query("""
                    select claim_generation from mq_consumed_message
                    where topic = ? and consumer_group = ? and message_key = ? and claim_token = ?
                    """, (row, index) -> new MessageInboxLease(topic, consumerGroup, messageKey,
                    row.getLong("claim_generation"), token), topic, consumerGroup, messageKey, token)
                    .stream().findFirst();
        }
    }

    public boolean isSucceeded(String topic, String consumerGroup, String messageKey) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                select count(*) > 0 from mq_consumed_message
                where topic = ? and consumer_group = ? and message_key = ? and status = 'SUCCEEDED'
                """, Boolean.class, topic, consumerGroup, messageKey));
    }

    public boolean lockCurrent(MessageInboxLease lease) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Inbox business fence requires an active transaction");
        }
        return !jdbcTemplate.query("""
                select id from mq_consumed_message
                where topic = ? and consumer_group = ? and message_key = ?
                  and status = 'PROCESSING' and claim_generation = ? and claim_token = ?
                  and locked_until > now(3)
                for update
                """, (row, index) -> row.getLong("id"), lease.topic(), lease.consumerGroup(),
                lease.messageKey(), lease.generation(), lease.token()).isEmpty();
    }

    public boolean markSucceeded(MessageInboxLease lease) {
        return jdbcTemplate.update("""
                update mq_consumed_message
                set status = 'SUCCEEDED', consumed_at = now(3), locked_until = null,
                    last_error = null, claim_token = null
                where topic = ? and consumer_group = ? and message_key = ?
                  and status = 'PROCESSING' and claim_generation = ? and claim_token = ?
                  and locked_until > now(3)
                """, lease.topic(), lease.consumerGroup(), lease.messageKey(),
                lease.generation(), lease.token()) == 1;
    }

    public boolean markFailed(MessageInboxLease lease, Throwable failure) {
        String message = failure == null || failure.getMessage() == null
                ? "Unknown consume failure" : failure.getMessage();
        return jdbcTemplate.update("""
                update mq_consumed_message
                set status = 'FAILED', locked_until = null, claim_token = null, last_error = ?
                where topic = ? and consumer_group = ? and message_key = ?
                  and status = 'PROCESSING' and claim_generation = ? and claim_token = ?
                  and locked_until > now(3)
                """, message.length() <= 500 ? message : message.substring(0, 500),
                lease.topic(), lease.consumerGroup(), lease.messageKey(),
                lease.generation(), lease.token()) == 1;
    }
}
