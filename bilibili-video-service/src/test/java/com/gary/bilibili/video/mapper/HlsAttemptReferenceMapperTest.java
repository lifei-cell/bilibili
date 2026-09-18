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

class HlsAttemptReferenceMapperTest {

    @Test
    void referencesAndLiveLeaseProtectPublishedObjects() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:attempt-references;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("create table video_transcode_task (file_md5 varchar(64), "
                + "output_url varchar(500), cover_url varchar(500), variants_json varchar(1000), "
                + "claim_generation bigint, claim_token varchar(64), lease_until timestamp, status int)");
        jdbc.execute("create table video (play_url varchar(500), cover_url varchar(500))");
        String prefix = "/videos/play/abc/attempt-1-token/";
        jdbc.update("insert into video_transcode_task values (?, ?, ?, ?, ?, ?, "
                        + "dateadd('hour', 1, current_timestamp), ?)",
                "abc", "https://cdn" + prefix + "master.m3u8", null, null, 1L, "token", 3);

        Configuration configuration = new Configuration(new Environment(
                "references-test", new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(VideoTranscodeTaskMapper.class);
        SqlSessionFactory sessions = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sessions.openSession(true)) {
            VideoTranscodeTaskMapper mapper = session.getMapper(VideoTranscodeTaskMapper.class);
            assertThat(mapper.countAttemptReferences(prefix)).isEqualTo(1);
            assertThat(mapper.countActiveAttempt("abc", 1, "token")).isEqualTo(1);

            jdbc.update("update video_transcode_task set output_url = null, claim_token = null, "
                    + "lease_until = null");
            jdbc.update("insert into video values (?, null)",
                    "https://cdn" + prefix + "master.m3u8");
            session.clearCache();
            assertThat(mapper.countAttemptReferences(prefix)).isEqualTo(1);
            assertThat(mapper.countActiveAttempt("abc", 1, "token")).isZero();

            jdbc.update("delete from video");
            session.clearCache();
            assertThat(mapper.countAttemptReferences(prefix)).isZero();
        }
    }
}
