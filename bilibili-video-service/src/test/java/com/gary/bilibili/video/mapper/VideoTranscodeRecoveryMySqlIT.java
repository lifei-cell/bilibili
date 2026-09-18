package com.gary.bilibili.video.mapper;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
class VideoTranscodeRecoveryMySqlIT {

    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("bilibili")
            .withUsername("bilibili")
            .withPassword("bilibili_test");

    @Test
    void expiredPlayableAttemptIsReclaimedWithoutReplacingLowRendition() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
        Flyway.configure().dataSource(dataSource).locations("classpath:db/migration").load().migrate();
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.update("insert into video_transcode_task (task_id, user_id, file_md5, file_name, "
                        + "file_size, source_url, status, retry_count, claim_generation, claim_token, "
                        + "lease_until, rendition_status, rendition_retry_count, output_url) "
                        + "values ('recovery-1', 1, 'abc', 'demo.mp4', 1, 'http://source', "
                        + "3, 0, 1, 'owner-1', timestampadd(SECOND, -1, now(6)), 1, 0, "
                        + "'http://media/old/low.m3u8')");

        Configuration configuration = new Configuration(new Environment(
                "mysql-recovery", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(VideoTranscodeTaskMapper.class);
        SqlSessionFactory sessions = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sessions.openSession(true)) {
            VideoTranscodeTaskMapper mapper = session.getMapper(VideoTranscodeTaskMapper.class);
            assertThat(mapper.selectDispatchable(10)).hasSize(1);
            assertThat(mapper.markDispatched("recovery-1", 1, "owner-2", 120, 3)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select rendition_status from video_transcode_task "
                    + "where task_id = 'recovery-1'", Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("select rendition_retry_count from video_transcode_task "
                    + "where task_id = 'recovery-1'", Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select output_url from video_transcode_task "
                    + "where task_id = 'recovery-1'", String.class)).isEqualTo("http://media/old/low.m3u8");
            assertThat(mapper.markProcessing("recovery-1", 2, "owner-2")).isEqualTo(1);
            assertThat(mapper.markProcessing("recovery-1", 2, "owner-2")).isZero();
            assertThat(mapper.markSuccess("recovery-1", 1, "owner-1", "http://media/stale/master.m3u8",
                    "http://media/stale/cover.jpg", "[]", true)).isZero();
            assertThat(mapper.markSuccess("recovery-1", 2, "owner-2", "http://media/new/master.m3u8",
                    "http://media/new/cover.jpg", "[]", true)).isEqualTo(1);
            assertThat(jdbc.queryForObject("select output_url from video_transcode_task "
                    + "where task_id = 'recovery-1'", String.class)).isEqualTo("http://media/new/master.m3u8");
            assertThat(jdbc.queryForObject("select rendition_status from video_transcode_task "
                    + "where task_id = 'recovery-1'", Integer.class)).isEqualTo(2);

            jdbc.update("insert into video_transcode_task (task_id, user_id, file_md5, file_name, "
                    + "file_size, source_url, status, retry_count, claim_generation, "
                    + "rendition_status, rendition_retry_count, output_url) values "
                    + "('recovery-2', 1, 'def', 'demo.mp4', 1, 'http://source', "
                    + "3, 0, 1, 4, 3, 'http://media/old/low.m3u8')");
            assertThat(mapper.requeueFailedRenditions("recovery-2")).isEqualTo(1);
            assertThat(mapper.requeueFailedRenditions("recovery-2")).isZero();
            assertThat(jdbc.queryForObject("select output_url from video_transcode_task "
                    + "where task_id = 'recovery-2'", String.class)).isEqualTo("http://media/old/low.m3u8");
            assertThat(mapper.markDispatched("recovery-2", 1, "owner-3", 120, 3)).isEqualTo(1);
        }
    }
}
