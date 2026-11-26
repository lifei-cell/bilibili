package com.gary.bilibili.video.reliability;

import com.gary.bilibili.common.reliability.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

@Testcontainers
abstract class MessageLifecycleMySqlSupport {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("bilibili").withUsername("bilibili").withPassword("bilibili_test");
    protected DriverManagerDataSource source;
    protected JdbcTemplate jdbc;
    protected DataSourceTransactionManager manager;
    protected OutboxEventRepository outbox;
    protected MessageInboxRepository inbox;
    protected MessageArchiveRepository archive;
    protected MessageReplayService replays;

    @BeforeEach
    void initialize() {
        source = new DriverManagerDataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        manager = new DataSourceTransactionManager(source);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        for (String table : new String[]{"mq_replay_audit", "reliable_event_outbox_archive", "reliable_event_outbox",
                "mq_inbox_receipt", "mq_consumed_message", "mq_failed_message"}) {
            jdbc.update("delete from " + table);
        }
        outbox = new OutboxEventRepository(jdbc);
        inbox = new MessageInboxRepository(jdbc);
        archive = new MessageArchiveRepository(jdbc, manager);
        replays = new MessageReplayService(jdbc, manager,
                new MessageLifecyclePolicy(true, 7, 30, 20, 2, 60), "test", "test-producer");
    }

    protected long event(String eventId) {
        outbox.insert(eventId, "test", "video", "42", "VIEW", "test-topic", "payload", "{\"key\":42}");
        return jdbc.queryForObject("select id from reliable_event_outbox where event_id = ?", Long.class, eventId);
    }

    protected void succeeded(String key) {
        var claim = inbox.acquire("test-topic", "test-group", key, 60).orElseThrow();
        if (!inbox.markSucceeded(claim)) throw new IllegalStateException("Test lease lost");
        jdbc.update("update mq_consumed_message set consumed_at = timestampadd(DAY, -31, now(3)) where message_key = ?", key);
    }

    protected int count(String table) { return jdbc.queryForObject("select count(*) from " + table, Integer.class); }

    protected MessageReplayRequest request(String requestId, int version) {
        return new MessageReplayRequest(requestId, version, "Dependency repaired and replay approved");
    }
}
