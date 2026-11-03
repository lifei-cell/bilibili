package com.gary.bilibili.video.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.common.reliability.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

class MessageArchiveMySqlIT extends MessageLifecycleMySqlSupport {
    @Test
    void archivesOnlyOldPublishedRowsInBoundedBatchesAndRetainsEventIdentity() {
        long old = event("old");
        long recent = event("recent");
        event("pending");
        jdbc.update("update reliable_event_outbox set status='PUBLISHED', published_at=now(3) where id in (?, ?)", old, recent);
        jdbc.update("update reliable_event_outbox set published_at=timestampadd(DAY,-8,now(3)) where id=?", old);
        assertThat(archive.archivePublished("another-owner", 7, 1)).isZero();
        assertThat(archive.archivePublished("test", 7, 1)).isEqualTo(1);
        assertThat(count("reliable_event_outbox_archive")).isEqualTo(1);
        assertThat(count("reliable_event_outbox")).isEqualTo(2);
        assertThat(outbox.insert("old", "test", "video", "42", "VIEW", "test-topic", "payload", "{}")).isFalse();
        assertThat(archive.archivePublished("test", 7, 1)).isZero();
        assertThat(jdbc.queryForObject("select payload->>'$.key' from reliable_event_outbox_archive", String.class)).isEqualTo("42");
    }

    @Test
    void copyFailureRollsBackDeletion() {
        event("copied-before-failure");
        long id = event("collision");
        jdbc.update("update reliable_event_outbox set status='PUBLISHED', published_at=timestampadd(DAY,-8,now(3))");
        jdbc.update("insert into reliable_event_outbox_archive select o.*, now(3) from reliable_event_outbox o where id=?", id);
        assertThatThrownBy(() -> archive.archivePublished("test", 7, 10)).isInstanceOf(RuntimeException.class);
        assertThat(count("reliable_event_outbox")).isEqualTo(2);
        assertThat(count("reliable_event_outbox_archive")).isEqualTo(1);
    }

    @Test
    void oldReplayAfterCompactionNeverRunsBusinessAndResolvesExistingDlq() {
        succeeded("old-key");
        var pending = inbox.acquire("test-topic", "test-group", "pending", 60).orElseThrow();
        assertThat(inbox.markFailed(pending, new IllegalStateException("failed"))).isTrue();
        var recent = inbox.acquire("test-topic", "test-group", "recent", 60).orElseThrow();
        assertThat(inbox.markSucceeded(recent)).isTrue();
        var failures = new FailedMessageRepository(jdbc);
        var payload = new Payload("old-key");
        String serialized = "{\"key\":\"old-key\"}";
        failures.saveFailure("test-topic", "test-group", "old-key", Payload.class.getName(), serialized,
                new IllegalStateException("historical failure"), null, 3);
        jdbc.update("update mq_failed_message set status='REPLAYED'");
        assertThat(archive.compactInbox(30, 1)).isEqualTo(1);
        assertThat(count("mq_consumed_message")).isEqualTo(2);
        assertThat(count("mq_inbox_receipt")).isEqualTo(1);
        AtomicInteger effects = new AtomicInteger();
        var executor = new ReliableMessageExecutor(failures, inbox, new MessageInboxLeaseGuard(inbox, manager),
                new ObjectMapper(), new SimpleMeterRegistry(), 3, Duration.ZERO, 60, true);
        executor.execute("test-topic", "test-group", "old-key", payload, effects::incrementAndGet);
        assertThat(effects).hasValue(0);
        assertThat(jdbc.queryForObject("select status from mq_failed_message", String.class)).isEqualTo("RESOLVED");
        assertThat(inbox.acquire("test-topic", "another-group", "old-key", 60)).isPresent();
    }

    @Test
    void archiveBetweenReceiptReadAndHotInsertDoesNotCreateANewLease() throws Exception {
        succeeded("race");
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean firstRead = new AtomicBoolean(true);
        JdbcTemplate paused = new JdbcTemplate(source) {
            @Override
            public <T> List<T> query(String sql, RowMapper<T> mapper, Object... args) {
                List<T> result = super.query(sql, mapper, args);
                if (sql.contains("from mq_inbox_receipt") && firstRead.getAndSet(false)) {
                    read.countDown();
                    try { if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("not resumed"); }
                    catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
                }
                return result;
            }
        };
        try (var worker = Executors.newSingleThreadExecutor()) {
            var result = worker.submit(() -> new MessageInboxRepository(paused).acquire("test-topic", "test-group", "race", 60));
            try {
                assertThat(read.await(5, TimeUnit.SECONDS)).isTrue();
                assertThat(archive.compactInbox(30, 10)).isEqualTo(1);
            } finally { resume.countDown(); }
            assertThat(result.get(5, TimeUnit.SECONDS)).isEmpty();
        }
        assertThat(count("mq_consumed_message")).isZero();
        assertThat(inbox.isSucceeded("test-topic", "test-group", "race")).isTrue();
    }

    @Test
    void repeatableReadSnapshotCannotHideCommittedReceipt() throws Exception {
        succeeded("snapshot");
        new TransactionTemplate(manager).executeWithoutResult(tx -> {
            assertThat(count("mq_inbox_receipt")).isZero(); // Establish a consistent read before archival.
            try (var worker = Executors.newSingleThreadExecutor()) {
                assertThat(worker.submit(() -> archive.compactInbox(30, 10)).get(5, TimeUnit.SECONDS)).isEqualTo(1);
            } catch (Exception e) { throw new IllegalStateException(e); }
            assertThat(inbox.acquire("test-topic", "test-group", "snapshot", 60)).isEmpty();
        });
    }

    @Test
    void concurrentArchiveWorkersMoveEachRowOnce() throws Exception {
        for (int i = 0; i < 8; i++) succeeded("key-" + i);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> archive.compactInbox(30, 4));
            var b = workers.submit(() -> archive.compactInbox(30, 4));
            assertThat(a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS)).isEqualTo(8);
        }
        assertThat(count("mq_inbox_receipt")).isEqualTo(8);
        assertThat(count("mq_consumed_message")).isZero();
    }

    public record Payload(String key) { }
}
