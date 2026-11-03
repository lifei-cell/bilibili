package com.gary.bilibili.video.reliability;

import com.gary.bilibili.common.reliability.OutboxEventLease;
import com.gary.bilibili.common.reliability.OutboxEventRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class OutboxLeaseMySqlIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("bilibili").withUsername("bilibili").withPassword("bilibili_test");

    private JdbcTemplate jdbc;
    private OutboxEventRepository first;
    private OutboxEventRepository second;
    private long id;

    @BeforeEach
    void setUp() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        first = new OutboxEventRepository(jdbc);
        second = new OutboxEventRepository(new JdbcTemplate(source));
        jdbc.update("delete from reliable_event_outbox");
        first.insert("event", "test", "video", "1", "VIEW", "test", "payload", "{}");
        id = jdbc.queryForObject("select id from reliable_event_outbox", Long.class);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void resumedOldPublisherCannotOverwriteNewAttempt(boolean success) throws Exception {
        OutboxEventLease old = first.claim(id, 60, 100).orElseThrow();
        CountDownLatch resume = new CountDownLatch(1);
        try (var workers = Executors.newSingleThreadExecutor()) {
            var oldResult = workers.submit(() -> {
                if (!resume.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("not resumed");
                return success ? first.markPublished(old)
                        : first.markFailed(old, "old failure", LocalDateTime.now());
            });
            try {
                expire();
                OutboxEventLease current = second.claim(id, 60, 100).orElseThrow();
                assertThat(current.token()).isNotEqualTo(old.token());
                resume.countDown();
                assertThat(oldResult.get(5, TimeUnit.SECONDS)).isFalse();
                assertThat(jdbc.queryForObject("select claim_token from reliable_event_outbox", String.class))
                        .isEqualTo(current.token());
                assertThat(second.markPublished(current)).isTrue();
                assertThat(first.markPublished(old)).isFalse();
                assertThat(first.findDispatchable("test", 10, 60, 100)).isEmpty();
            } finally {
                resume.countDown();
            }
        }
    }

    @Test
    void twoInstancesCompetingForOneRowHaveOnlyOneWinner() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var a = workers.submit(() -> { start.await(5, TimeUnit.SECONDS); return first.claim(id, 60, 100); });
            var b = workers.submit(() -> { start.await(5, TimeUnit.SECONDS); return second.claim(id, 60, 100); });
            start.countDown();
            assertThat(a.get(5, TimeUnit.SECONDS).isPresent() ^ b.get(5, TimeUnit.SECONDS).isPresent()).isTrue();
            assertThat(jdbc.queryForObject("select attempt_count from reliable_event_outbox", Integer.class)).isEqualTo(1);
        }
    }

    @Test
    void expiryWithoutTakeoverRejectsBothCompletionPaths() {
        OutboxEventLease lease = first.claim(id, 60, 100).orElseThrow();
        expire();
        assertThat(first.markPublished(lease)).isFalse();
        assertThat(first.markFailed(lease, "expired", LocalDateTime.now())).isFalse();
        assertThat(first.findDispatchable("test", 10, 60, 100)).hasSize(1);
    }

    @Test
    void failureRespectsRetryScheduleAndAttemptLimit() {
        OutboxEventLease lease = first.claim(id, 60, 2).orElseThrow();
        LocalDateTime future = jdbc.queryForObject("select timestampadd(HOUR, 1, now(3))", LocalDateTime.class);
        assertThat(first.markFailed(lease, "failure", future)).isTrue();
        assertThat(second.claim(id, 60, 2)).isEmpty();
        jdbc.update("update reliable_event_outbox set available_at = timestampadd(SECOND, -1, now(3))");
        OutboxEventLease retry = second.claim(id, 60, 2).orElseThrow();
        assertThat(first.markFailed(lease, "stale", LocalDateTime.now())).isFalse();
        assertThat(second.markFailed(retry, "again", LocalDateTime.now().minusSeconds(1))).isTrue();
        jdbc.update("update reliable_event_outbox set available_at = timestampadd(SECOND, -1, now(3))");
        assertThat(first.claim(id, 60, 2)).isEmpty();
        assertThat(first.findDispatchable("test", 10, 60, 2)).isEmpty();
    }

    @Test
    void migrationPreservesPublishedRowsAndReclaimsLegacyProcessingRows() {
        var source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        var legacy = Flyway.configure().dataSource(source).locations("classpath:db/migration")
                .cleanDisabled(false).target("9").load();
        legacy.clean();
        legacy.migrate();
        jdbc.update("""
                insert into reliable_event_outbox
                (event_id, owner, aggregate_type, aggregate_id, event_type, topic, payload_type, payload)
                values ('old','test','video','1','VIEW','test','payload','{}'),
                       ('done','test','video','2','VIEW','test','payload','{}')
                """);
        jdbc.update("update reliable_event_outbox set status = 'PROCESSING', locked_at = timestampadd(SECOND, -61, now(3)) where event_id = 'old'");
        jdbc.update("update reliable_event_outbox set status = 'PUBLISHED' where event_id = 'done'");
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        var events = first.findDispatchable("test", 10, 60, 100);
        assertThat(events).hasSize(1);
        var lease = first.claim(events.getFirst().id(), 60, 100).orElseThrow();
        assertThat(first.markPublished(lease)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from reliable_event_outbox where status = 'PUBLISHED'", Integer.class)).isEqualTo(2);
    }

    private void expire() {
        jdbc.update("update reliable_event_outbox set locked_until = timestampadd(SECOND, -1, now(3))");
    }
}
