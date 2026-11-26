package com.gary.bilibili.video.reliability;

import com.gary.bilibili.common.reliability.MessageInboxRepository;
import com.gary.bilibili.common.reliability.MessageInboxLease;
import com.gary.bilibili.common.reliability.MessageInboxLeaseGuard;
import com.gary.bilibili.common.reliability.MessageInboxLeaseLostException;
import com.gary.bilibili.common.reliability.FailedMessageRepository;
import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class MessageInboxLeaseMySqlIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("bilibili").withUsername("bilibili").withPassword("bilibili_test");

    private JdbcTemplate jdbc;
    private MessageInboxRepository inbox;
    private MessageInboxLeaseGuard guard;
    private ReliableMessageExecutor executor;

    @BeforeEach
    void setUp() {
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        inbox = new MessageInboxRepository(jdbc);
        guard = new MessageInboxLeaseGuard(inbox, new DataSourceTransactionManager(dataSource));
        executor = new ReliableMessageExecutor(new FailedMessageRepository(jdbc), inbox, guard,
                new ObjectMapper(), new SimpleMeterRegistry(), 3, Duration.ZERO, 60, true);
        jdbc.update("delete from mq_consumed_message");
        jdbc.update("delete from mq_failed_message");
        jdbc.execute("create table if not exists inbox_business_effect "
                + "(id int primary key, effect_value varchar(64), effect_count int not null default 0)");
        jdbc.update("delete from inbox_business_effect");
        jdbc.update("insert into inbox_business_effect (id, effect_value) values (1, 'initial')");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void oldConsumerCannotCompleteAfterExpiredLeaseIsTakenOver(boolean success) {
        MessageInboxLease old = claim();
        expire();
        MessageInboxLease current = claim();
        assertThat(current.generation()).isEqualTo(old.generation() + 1);
        assertThat(current.token()).isNotEqualTo(old.token());

        if (success) {
            assertThat(inbox.markSucceeded(old)).isFalse();
        } else {
            assertThat(inbox.markFailed(old, new IllegalStateException("old owner"))).isFalse();
        }

        assertThat(jdbc.queryForObject("select status from mq_consumed_message", String.class))
                .isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("select claim_token from mq_consumed_message", String.class))
                .isEqualTo(current.token());
        assertThat(inbox.markSucceeded(current)).isTrue();
    }

    @Test
    void expiredClaimWithoutTakeoverCannotWriteBusinessOrComplete() {
        MessageInboxLease old = claim();
        expire();

        assertThatThrownBy(() -> complete(old, () -> writeEffect("old")))
                .isInstanceOf(MessageInboxLeaseLostException.class);
        assertThat(inbox.markSucceeded(old)).isFalse();
        assertThat(inbox.markFailed(old, new IllegalStateException("expired"))).isFalse();
        assertEffect("initial", 0);
    }

    @Test
    void staleConsumerCannotOverwriteNewBusinessOrResolveItsFailure() {
        MessageInboxLease old = claim();
        expire();
        MessageInboxLease current = claim();
        FailedMessageRepository failures = new FailedMessageRepository(jdbc);
        failures.saveFailure(old.topic(), old.consumerGroup(), old.messageKey(), "payload", "{}",
                new IllegalStateException("existing failure"), null, 1);

        complete(current, () -> writeEffect("new"));
        assertThatThrownBy(() -> guard.run(old, () -> {
            writeEffect("old");
            failures.markResolved(old.topic(), old.consumerGroup(), old.messageKey(), "{}");
        })).isInstanceOf(MessageInboxLeaseLostException.class);

        assertEffect("new", 1);
        assertThat(jdbc.queryForObject("select status from mq_failed_message", String.class)).isEqualTo("FAILED");
        assertThat(inbox.isSucceeded(old.topic(), old.consumerGroup(), old.messageKey())).isTrue();
    }

    @Test
    void expiryDuringBusinessActionRollsBackBusinessWrites() {
        MessageInboxLease lease = claim();

        assertThatThrownBy(() -> complete(lease, () -> {
            writeEffect("old");
            // Inject an expired deadline inside the transaction before its final CAS.
            expire();
        })).isInstanceOf(MessageInboxLeaseLostException.class);

        assertEffect("initial", 0);
        assertThat(jdbc.queryForObject("select status from mq_consumed_message", String.class))
                .isEqualTo("PROCESSING");
        assertThat(jdbc.queryForObject("select claim_token from mq_consumed_message", String.class))
                .isEqualTo(lease.token());
    }

    @Test
    void takeoverWaitsForExpiredInFlightTransactionAndOnlyNewBusinessCommits() throws Exception {
        MessageInboxLease old = claim();
        jdbc.update("update mq_consumed_message set locked_until = timestampadd(MICROSECOND, 1500000, now(3))");
        CountDownLatch oldEntered = new CountDownLatch(1);
        CountDownLatch resumeOld = new CountDownLatch(1);
        CountDownLatch takeoverStarted = new CountDownLatch(1);
        try (var workers = Executors.newFixedThreadPool(2)) {
            var oldResult = workers.submit(() -> complete(old, () -> {
                writeEffect("old");
                oldEntered.countDown();
                await(resumeOld);
            }));
            try {
                assertThat(oldEntered.await(5, TimeUnit.SECONDS)).isTrue();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (!Boolean.TRUE.equals(jdbc.queryForObject(
                        "select locked_until <= now(3) from mq_consumed_message", Boolean.class))) {
                    assertThat(System.nanoTime()).isLessThan(deadline);
                    TimeUnit.MILLISECONDS.sleep(10);
                }
                var currentResult = workers.submit(() -> {
                    takeoverStarted.countDown();
                    MessageInboxLease current = claim();
                    complete(current, () -> writeEffect("new"));
                    return current;
                });
                assertThat(takeoverStarted.await(5, TimeUnit.SECONDS)).isTrue();
                // Expiry does not permit a competing claim to bypass an active business row lock.
                assertThatThrownBy(() -> currentResult.get(200, TimeUnit.MILLISECONDS))
                        .isInstanceOf(TimeoutException.class);
                resumeOld.countDown();
                assertThatThrownBy(() -> oldResult.get(5, TimeUnit.SECONDS))
                        .hasCauseInstanceOf(MessageInboxLeaseLostException.class);
                assertThat(currentResult.get(5, TimeUnit.SECONDS).generation()).isEqualTo(2);
            } finally {
                resumeOld.countDown();
            }
        }
        assertEffect("new", 1);
        assertThat(jdbc.queryForObject("select status from mq_consumed_message", String.class))
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void retryRollsBackPartialBusinessBeforeSuccessfulAttempt() {
        AtomicInteger attempts = new AtomicInteger();
        execute(() -> {
            writeEffect("new");
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("failure after business write");
            }
        });

        assertThat(attempts.get()).isEqualTo(2);
        assertEffect("new", 1);
        assertThat(jdbc.queryForObject("select status from mq_consumed_message", String.class))
                .isEqualTo("SUCCEEDED");
    }

    @Test
    void finalFailureAndDlqCommitTogetherWithoutBusinessEffects() {
        assertThatThrownBy(() -> execute(() -> {
            writeEffect("old");
            throw new IllegalStateException("injected business failure");
        })).isInstanceOf(ReliableMessageExecutor.MessageProcessingException.class);

        assertEffect("initial", 0);
        assertThat(jdbc.queryForObject("select status from mq_consumed_message", String.class)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("select count(*) from mq_failed_message", Integer.class)).isEqualTo(1);
        execute(() -> writeEffect("new"));
        assertEffect("new", 1);
        assertThat(jdbc.queryForObject("select status from mq_failed_message", String.class)).isEqualTo("RESOLVED");
        execute(() -> writeEffect("duplicate"));
        assertEffect("new", 1);
    }

    @Test
    void legacyMigrationPreservesCompletedRowsAndReclaimsExpiredRows() {
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway legacy = Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .cleanDisabled(false).target("8").load();
        legacy.clean();
        legacy.migrate();
        jdbc.update("insert into mq_consumed_message (topic, consumer_group, message_key, status, locked_until) "
                + "values ('lease-test', 'test-consumer', 'message-1', 'PROCESSING', timestampadd(SECOND, -1, now(3))), "
                + "('lease-test', 'test-consumer', 'completed', 'SUCCEEDED', null)");
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();

        assertThat(claim().generation()).isEqualTo(1);
        assertThat(inbox.acquire("lease-test", "test-consumer", "completed", 60)).isEmpty();
        assertThat(inbox.isSucceeded("lease-test", "test-consumer", "completed")).isTrue();
    }

    private MessageInboxLease claim() {
        return inbox.acquire("lease-test", "test-consumer", "message-1", 60).orElseThrow();
    }

    private void expire() {
        jdbc.update("update mq_consumed_message set locked_until = timestampadd(SECOND, -1, now(3))");
    }

    private void complete(MessageInboxLease lease, Runnable action) {
        guard.run(lease, () -> {
            action.run();
            if (!inbox.markSucceeded(lease)) {
                throw new MessageInboxLeaseLostException(lease);
            }
        });
    }

    private void execute(Runnable action) {
        executor.execute("lease-test", "test-consumer", "message-1", new TestPayload("message-1"), action);
    }

    private void writeEffect(String value) {
        jdbc.update("update inbox_business_effect set effect_value = ?, effect_count = effect_count + 1 where id = 1", value);
    }

    private void assertEffect(String value, int count) {
        assertThat(jdbc.queryForObject("select effect_value from inbox_business_effect where id = 1", String.class))
                .isEqualTo(value);
        assertThat(jdbc.queryForObject("select effect_count from inbox_business_effect where id = 1", Integer.class))
                .isEqualTo(count);
    }

    private static void await(CountDownLatch signal) {
        try {
            if (!signal.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("test consumer was not resumed");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        }
    }

    private record TestPayload(String id) {
    }
}
