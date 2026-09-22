package com.gary.bilibili.video.repository;

import com.gary.bilibili.video.model.CreatorDirtyDay;
import com.gary.bilibili.video.service.CreatorAnalyticsAggregator;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class CreatorAnalyticsMySqlIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("bilibili")
            .withUsername("bilibili")
            .withPassword("bilibili_test");

    private JdbcTemplate jdbc;
    private CreatorAnalyticsRepository repository;
    private CreatorAnalyticsAggregator aggregator;
    private TransactionTemplate transactions;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration")
                .cleanDisabled(false).load().clean();
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(dataSource);
        repository = new CreatorAnalyticsRepository(jdbc);
        aggregator = new CreatorAnalyticsAggregator(repository);
        transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        seedUsersAndVideos();
    }

    @Test
    void aggregationIsIdempotentLateSafeAndCreatorIsolated() {
        LocalDate day = LocalDate.of(2026, 9, 20);
        LocalDateTime noon = day.atTime(12, 0);
        assertThat(repository.insertViewEvent("view-1", 101L, noon)).isTrue();
        assertThat(repository.insertViewEvent("view-1", 101L, noon)).isFalse();
        repository.markDirtyForVideo(101L, day);
        jdbc.update("insert into user_like (user_id, target_type, target_id, status, create_time) "
                + "values (2, 1, 101, 1, ?)", noon);
        jdbc.update("insert into collection (user_id, video_id, folder_id, status, create_time) "
                + "values (2, 101, 0, 1, ?)", noon);
        jdbc.update("insert into comment (user_id, video_id, content, status, deleted, create_time) "
                + "values (2, 101, 'comment', 0, 0, ?)", noon);
        jdbc.update("insert into danmu (user_id, video_id, content, send_time) "
                + "values (2, 101, 'danmu', ?)", noon);

        rebuild(1L, day);
        rebuild(1L, day);
        assertThat(jdbc.queryForObject("select count(*) from creator_video_daily_stats "
                + "where creator_id=1 and video_id=101 and stat_date='2026-09-20'", Long.class)).isEqualTo(1);
        assertThat(repository.findTrend(1L, day, day).getFirst())
                .extracting("viewCount", "likeCount", "collectCount", "commentCount", "danmuCount")
                .containsExactly(1L, 1L, 1L, 1L, 1L);
        assertThat(repository.findTrend(2L, day, day)).isEmpty();

        CreatorDirtyDay dirty = repository.findDirtyDays(10).getFirst();
        Long claimedVersion = repository.claimDirtyDay(dirty, "worker-1", 60);
        assertThat(claimedVersion).isEqualTo(1L);
        assertThat(repository.insertViewEvent("late-view", 101L, day.atTime(1, 0))).isTrue();
        repository.markDirtyForVideo(101L, day);
        repository.completeDirtyDay(dirty, "worker-1", claimedVersion);
        assertThat(repository.findDirtyDays(10)).hasSize(1);

        Long latestVersion = repository.claimDirtyDay(dirty, "worker-2", 60);
        assertThat(latestVersion).isEqualTo(2L);
        rebuild(1L, day);
        repository.completeDirtyDay(dirty, "worker-2", latestVersion);

        assertThat(repository.findDirtyDays(10)).isEmpty();
        assertThat(repository.findTrend(1L, day, day).getFirst().viewCount()).isEqualTo(2L);
        assertThat(repository.findTopVideos(1L, day, day, 10).getFirst().videoId()).isEqualTo(101L);
        assertThat(repository.findTopVideos(2L, day, day, 10)).isEmpty();
    }

    private void rebuild(long creatorId, LocalDate day) {
        transactions.executeWithoutResult(status -> aggregator.rebuildDay(creatorId, day));
    }

    private void seedUsersAndVideos() {
        jdbc.update("insert into sys_user (id, username, nickname, phone) values "
                + "(1, 'creator-one', 'Creator One', '13000000001'), "
                + "(2, 'creator-two', 'Creator Two', '13000000002')");
        jdbc.update("insert into video (id, user_id, title, file_md5, status) values "
                + "(101, 1, 'owned-one', 'md5-101', 1), "
                + "(102, 2, 'owned-two', 'md5-102', 1)");
    }
}
