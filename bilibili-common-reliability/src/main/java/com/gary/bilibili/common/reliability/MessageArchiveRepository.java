package com.gary.bilibili.common.reliability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Repository
@ConditionalOnProperty(name = "spring.datasource.url")
public class MessageArchiveRepository {
    private static final String OUTBOX_COLUMNS = """
            id, event_id, owner, aggregate_type, aggregate_id, event_type, topic, payload_type, payload,
            status, attempt_count, available_at, locked_at, published_at, last_error, create_time,
            update_time, claim_token, locked_until, replay_count, last_replay_time
            """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public MessageArchiveRepository(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc;
        this.transactions = new TransactionTemplate(manager);
    }

    public int archivePublished(String owner, int days, int batchSize) {
        return transactions.execute(status -> {
            var ids = jdbc.queryForList("""
                    select id from reliable_event_outbox
                    where owner = ? and status = 'PUBLISHED' and published_at < timestampadd(DAY, -?, now(3))
                    order by published_at, id limit ? for update skip locked
                    """, Long.class, owner, days, batchSize);
            for (long id : ids) {
                // Copy and delete share the source row lock and transaction. Any copy failure rolls back both.
                jdbc.update("""
                        insert into reliable_event_outbox_archive (%s)
                        select %s from reliable_event_outbox where id = ?
                        """.formatted(OUTBOX_COLUMNS, OUTBOX_COLUMNS), id);
                jdbc.update("delete from reliable_event_outbox where id = ?", id);
            }
            return ids.size();
        });
    }

    public int compactInbox(int days, int batchSize) {
        return transactions.execute(status -> {
            var ids = jdbc.queryForList("""
                    select id from mq_consumed_message
                    where status = 'SUCCEEDED' and consumed_at < timestampadd(DAY, -?, now(3))
                    order by consumed_at, id limit ? for update skip locked
                    """, Long.class, days, batchSize);
            for (long id : ids) {
                jdbc.update("""
                        insert into mq_inbox_receipt (consumer_group, topic, message_key, consumed_at)
                        select consumer_group, topic, message_key, consumed_at from mq_consumed_message where id = ?
                        on duplicate key update consumed_at = mq_inbox_receipt.consumed_at
                        """, id);
                jdbc.update("delete from mq_consumed_message where id = ?", id);
            }
            return ids.size();
        });
    }
}
