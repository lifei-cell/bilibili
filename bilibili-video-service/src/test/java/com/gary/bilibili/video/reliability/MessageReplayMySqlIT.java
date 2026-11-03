package com.gary.bilibili.video.reliability;

import com.gary.bilibili.common.reliability.*;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

class MessageReplayMySqlIT extends MessageLifecycleMySqlSupport {
    @Test
    void finalFailureAndCrashedFinalLeaseBecomeExhaustedButActiveLeaseDoesNot() {
        long failed = event("failed");
        var lease = outbox.claim(failed, 60, 1).orElseThrow();
        assertThat(outbox.markFailed(lease, "broker down", LocalDateTime.now())).isTrue();
        long crashed = event("crashed");
        var old = outbox.claim(crashed, 60, 1).orElseThrow();
        long active = event("active");
        outbox.claim(active, 60, 1).orElseThrow();
        jdbc.update("update reliable_event_outbox set locked_until=timestampadd(SECOND,-1,now(3)) where id=?", crashed);
        assertThat(outbox.exhaust("test", 1, 60, 10)).isEqualTo(2);
        assertThat(outbox.countExhausted("test")).isEqualTo(2);
        assertThat(outbox.markPublished(old)).isFalse();
        var receipt = replays.replayOutbox(crashed, request("request-replay-001", 0));
        assertThat(receipt.eventId()).isEqualTo("crashed");
        assertThat(outbox.markFailed(old, "stale", LocalDateTime.now())).isFalse();
        assertThat(outbox.claim(crashed, 60, 1)).isPresent();
    }

    @Test
    void replayIsIdempotentAndAuditedAndCannotResetALaterCycleWithOldVersion() {
        long id = exhausted();
        var request = request("request-replay-002", 0);
        var receipt = replays.replayOutbox(id, request);
        assertThat(replays.replayOutbox(id, request)).isEqualTo(receipt);
        assertThat(count("mq_replay_audit")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select replay_count from reliable_event_outbox", Integer.class)).isEqualTo(1);
        jdbc.update("update reliable_event_outbox set status='EXHAUSTED', attempt_count=100");
        assertThatThrownBy(() -> replays.replayOutbox(id, request("request-replay-003", 0))).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> replays.replayOutbox(id, request("request-replay-003", 1))).isInstanceOf(IllegalStateException.class);
        jdbc.update("update reliable_event_outbox set last_replay_time=timestampadd(SECOND,-61,now(3))");
        replays.replayOutbox(id, request("request-replay-003", 1));
        jdbc.update("update reliable_event_outbox set status='EXHAUSTED', last_replay_time=timestampadd(SECOND,-61,now(3))");
        assertThatThrownBy(() -> replays.replayOutbox(id, request("request-replay-004", 2))).isInstanceOf(IllegalStateException.class);
        assertThat(count("mq_replay_audit")).isEqualTo(2);
    }

    @Test
    void concurrentRequestsForSameFailureScheduleOnlyOneReplay() throws Exception {
        long id = exhausted();
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> attempt(id, "request-concurrent-1", start));
            var b = workers.submit(() -> attempt(id, "request-concurrent-2", start));
            start.countDown();
            assertThat(a.get(5, TimeUnit.SECONDS) + b.get(5, TimeUnit.SECONDS)).isEqualTo(1);
        }
        assertThat(count("mq_replay_audit")).isEqualTo(1);
    }

    @Test
    void reusedRequestIdCannotMutateAnotherMessageOrChangeReason() {
        long first = exhausted();
        replays.replayOutbox(first, request("request-reused-001", 0));
        long second = event("second");
        jdbc.update("update reliable_event_outbox set status='EXHAUSTED' where id=?", second);
        assertThatThrownBy(() -> replays.replayOutbox(second, request("request-reused-001", 0))).isInstanceOf(IllegalArgumentException.class);
        assertThat(jdbc.queryForObject("select status from reliable_event_outbox where id=?", String.class, second)).isEqualTo("EXHAUSTED");
        assertThatThrownBy(() -> replays.replayOutbox(first,
                new MessageReplayRequest("request-reused-001", 0, "Different replay reason"))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void dlqReplayAtomicallyQueuesOriginalPayloadWithoutClearingDedupReceipts() {
        succeeded("consumed-key");
        archive.compactInbox(30, 10);
        var failures = new FailedMessageRepository(jdbc);
        failures.saveFailure("test-topic", "test-group", "consumed-key", "com.gary.bilibili.Payload",
                "{\"key\":\"consumed-key\"}", new IllegalStateException("old"), null, 3);
        long id = jdbc.queryForObject("select id from mq_failed_message", Long.class);
        var request = request("request-dlq-00001", 0);
        var receipt = replays.replayFailure(id, request);
        assertThat(replays.replayFailure(id, request)).isEqualTo(receipt);
        assertThat(count("reliable_event_outbox")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select payload->>'$.key' from reliable_event_outbox", String.class)).isEqualTo("consumed-key");
        assertThat(jdbc.queryForObject("select status from mq_failed_message", String.class)).isEqualTo("REPLAYED");
        assertThat(inbox.acquire("test-topic", "test-group", "consumed-key", 60)).isEmpty();
        assertThat(count("mq_inbox_receipt")).isEqualTo(1);
    }

    @Test
    void dlqEnqueueFailureRollsBackStateAndAudit() {
        new FailedMessageRepository(jdbc).saveFailure("test-topic", "test-group", "key", "payload", "{}",
                new IllegalStateException("failed"), null, 3);
        long id = jdbc.queryForObject("select id from mq_failed_message", Long.class);
        event("dlq-replay-" + id + "-1"); // Force an enqueue failure after source validation.
        assertThatThrownBy(() -> replays.replayFailure(id, request("request-rollback-1", 0))).isInstanceOf(RuntimeException.class);
        assertThat(jdbc.queryForObject("select status from mq_failed_message", String.class)).isEqualTo("FAILED");
        assertThat(count("mq_replay_audit")).isZero();
    }

    @Test
    void publishedOrArchivedMessagesCannotBeManuallyReplayed() {
        long id = event("published");
        jdbc.update("update reliable_event_outbox set status='PUBLISHED',published_at=timestampadd(DAY,-8,now(3))");
        assertThatThrownBy(() -> replays.replayOutbox(id, request("request-published-1", 0))).isInstanceOf(IllegalStateException.class);
        archive.archivePublished("test", 7, 10);
        assertThatThrownBy(() -> replays.replayOutbox(id, request("request-published-1", 0))).isInstanceOf(IllegalArgumentException.class);
    }

    private long exhausted() {
        long id = event("exhausted");
        jdbc.update("update reliable_event_outbox set status='EXHAUSTED',attempt_count=100 where id=?", id);
        return id;
    }

    private int attempt(long id, String requestId, CountDownLatch start) throws InterruptedException {
        if (!start.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("not started");
        try { replays.replayOutbox(id, request(requestId, 0)); return 1; }
        catch (IllegalStateException conflict) { return 0; }
    }
}
