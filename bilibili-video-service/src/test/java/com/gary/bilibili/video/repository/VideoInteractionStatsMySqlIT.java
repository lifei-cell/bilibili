package com.gary.bilibili.video.repository;

import com.gary.bilibili.common.social.VideoInteractionStats;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
class VideoInteractionStatsMySqlIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("bilibili")
            .withUsername("bilibili")
            .withPassword("bilibili_test");

    @Test
    void backfillAndTransactionalWritesMatchCommittedInteractionFacts() throws Exception {
        var dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).target("11").load().migrate();
        var jdbc = new JdbcTemplate(dataSource);
        var counts = new VideoInteractionStats(jdbc);
        var transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbc.update("insert into video (id, user_id, title, file_md5, status) values (101, 1, 'video', 'md5', 1)");
        jdbc.update("""
                insert into video_stats (video_id, view_count, danmu_count, like_count, collect_count, comment_count)
                values (101, 50, 4, 999, 888, 777)
                """);
        jdbc.update("""
                insert into user_like (user_id, target_type, target_id, status)
                values (1, 1, 101, 1), (2, 1, 101, 0), (1, 2, 101, 1)
                """);
        jdbc.update("insert into collection (user_id, video_id, folder_id, status) values (1, 101, 0, 1), (2, 101, 0, 0)");
        jdbc.update("""
                insert into comment (user_id, video_id, content, status, deleted)
                values (1, 101, 'visible', 0, 0), (2, 101, 'hidden', 1, 0), (2, 101, 'deleted', 0, 1)
                """);

        Flyway.configure().dataSource(dataSource).load().migrate();
        assertCounts(jdbc, 1L);

        assertThatThrownBy(() -> transactions.executeWithoutResult(status -> {
            like(jdbc, counts, 3L);
            throw new IllegalStateException("fail before commit");
        })).isInstanceOf(IllegalStateException.class).hasMessage("fail before commit");
        assertCounts(jdbc, 1L);
        assertThat(jdbc.queryForObject("select count(*) from user_like where user_id = 3", Long.class)).isZero();

        try (var pool = Executors.newFixedThreadPool(3)) {
            var first = pool.submit(() -> transactions.executeWithoutResult(status -> like(jdbc, counts, 3L)));
            var duplicate = pool.submit(() -> transactions.executeWithoutResult(status -> like(jdbc, counts, 3L)));
            var other = pool.submit(() -> transactions.executeWithoutResult(status -> like(jdbc, counts, 4L)));
            first.get(15, TimeUnit.SECONDS);
            duplicate.get(15, TimeUnit.SECONDS);
            other.get(15, TimeUnit.SECONDS);
        }
        assertCounts(jdbc, 3L);
        assertThat(jdbc.queryForObject("""
                select count(*) from user_like where target_type = 1 and target_id = 101 and status = 1
                """, Long.class)).isEqualTo(3);
    }

    private void like(JdbcTemplate jdbc, VideoInteractionStats counts, long userId) {
        int inserted = jdbc.update("""
                insert ignore into user_like (user_id, target_type, target_id, status) values (?, 1, 101, 1)
                """, userId);
        if (inserted > 0) {
            counts.changeLikes(101L, 1);
        }
    }

    private void assertCounts(JdbcTemplate jdbc, long likes) {
        assertThat(jdbc.queryForMap("""
                select view_count, danmu_count, like_count, collect_count, comment_count
                from video_stats where video_id = 101
                """)).containsExactlyInAnyOrderEntriesOf(Map.of(
                        "view_count", 50L, "danmu_count", 4L, "like_count", likes,
                        "collect_count", 1L, "comment_count", 1L));
    }
}
