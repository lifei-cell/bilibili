package com.gary.bilibili.common.reliability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(name = "spring.datasource.url")
public class MessageInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public MessageInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean acquire(String topic, String consumerGroup, String messageKey,
                           int leaseSeconds) {
        try {
            jdbcTemplate.update("""
                    insert into mq_consumed_message
                        (topic, consumer_group, message_key, status, locked_until)
                    values (?, ?, ?, 'PROCESSING', date_add(now(3), interval ? second))
                    """, topic, consumerGroup, messageKey, leaseSeconds);
            return true;
        } catch (DuplicateKeyException duplicate) {
            return jdbcTemplate.update("""
                    update mq_consumed_message
                    set status = 'PROCESSING', attempt_count = attempt_count + 1,
                        locked_until = date_add(now(3), interval ? second), last_error = null
                    where topic = ? and consumer_group = ? and message_key = ?
                      and (status = 'FAILED'
                           or (status = 'PROCESSING' and locked_until < now(3)))
                    """, leaseSeconds, topic, consumerGroup, messageKey) == 1;
        }
    }

    public void markSucceeded(String topic, String consumerGroup, String messageKey) {
        jdbcTemplate.update("""
                update mq_consumed_message
                set status = 'SUCCEEDED', consumed_at = now(3), locked_until = null,
                    last_error = null
                where topic = ? and consumer_group = ? and message_key = ?
                  and status = 'PROCESSING'
                """, topic, consumerGroup, messageKey);
    }

    public void markFailed(String topic, String consumerGroup, String messageKey,
                           Throwable failure) {
        String message = failure == null || failure.getMessage() == null
                ? "Unknown consume failure" : failure.getMessage();
        jdbcTemplate.update("""
                update mq_consumed_message
                set status = 'FAILED', locked_until = null, last_error = ?
                where topic = ? and consumer_group = ? and message_key = ?
                  and status = 'PROCESSING'
                """, message.length() <= 500 ? message : message.substring(0, 500),
                topic, consumerGroup, messageKey);
    }
}
