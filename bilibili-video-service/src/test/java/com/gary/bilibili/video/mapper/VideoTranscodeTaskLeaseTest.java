package com.gary.bilibili.video.mapper;

import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class VideoTranscodeTaskLeaseTest {

    @Test
    void expiredOwnerCannotRenewCompleteOrResetAfterReclaim() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:transcode-lease;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table video_transcode_task (task_id varchar(64) primary key, "
                + "status int not null, claim_generation bigint not null, claim_token varchar(64), "
                + "lease_until timestamp, next_retry_time timestamp, retry_count int not null, "
                + "error_message varchar(500), output_url varchar(500), cover_url varchar(500), "
                + "variants_json varchar(500), update_time timestamp not null)");
        jdbc.update("insert into video_transcode_task (task_id, status, claim_generation, retry_count, update_time) "
                + "values ('task-1', 0, 0, 0, current_timestamp)");

        Configuration configuration = new Configuration(new Environment(
                "lease-test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(VideoTranscodeTaskMapper.class);
        SqlSessionFactory sessions = new SqlSessionFactoryBuilder().build(configuration);

        try (SqlSession session = sessions.openSession(true)) {
            VideoTranscodeTaskMapper mapper = session.getMapper(VideoTranscodeTaskMapper.class);
            assertThat(mapper.selectDispatchable(10)).hasSize(1);
            assertThat(mapper.markDispatched("task-1", 0, "owner-1", 120)).isEqualTo(1);
            assertThat(mapper.markProcessing("task-1", 1, "owner-1")).isEqualTo(1);

            jdbc.update("update video_transcode_task set lease_until = dateadd('second', -1, current_timestamp) "
                    + "where task_id = 'task-1'");
            assertThat(mapper.markDispatched("task-1", 1, "owner-2", 120)).isEqualTo(1);
            assertThat(mapper.markProcessing("task-1", 2, "owner-2")).isEqualTo(1);

            assertThat(mapper.renewLease("task-1", 1, "owner-1", 120)).isZero();
            assertThat(mapper.markSuccess("task-1", 1, "owner-1", "old/master.m3u8",
                    "old/cover.jpg", "[]", true)).isZero();
            assertThat(mapper.markPendingAfterFailure("task-1", 1, "owner-1", 2,
                    "late failure", 3, 10)).isZero();
            assertThat(mapper.markDegradedSuccess("task-1", 1, "owner-1", "late failure")).isZero();

            assertThat(mapper.markSuccess("task-1", 2, "owner-2", "new/low.m3u8",
                    "new/cover.jpg", "[]", false)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select claim_token from video_transcode_task where task_id = 'task-1'", String.class))
                    .isEqualTo("owner-2");
            assertThat(mapper.markSuccess("task-1", 1, "owner-1", "old/master.m3u8",
                    "old/cover.jpg", "[]", true)).isZero();
            assertThat(mapper.markSuccess("task-1", 2, "owner-2", "new/master.m3u8",
                    "new/cover.jpg", "[]", true)).isEqualTo(1);
            assertThat(jdbc.queryForObject(
                    "select output_url from video_transcode_task where task_id = 'task-1'", String.class))
                    .isEqualTo("new/master.m3u8");
            assertThat(jdbc.queryForObject(
                    "select claim_token from video_transcode_task where task_id = 'task-1'", String.class))
                    .isNull();
        }
    }
}
