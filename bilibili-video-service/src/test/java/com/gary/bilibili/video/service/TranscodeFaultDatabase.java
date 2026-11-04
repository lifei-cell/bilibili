package com.gary.bilibili.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.message.VideoTranscodeMessage;
import org.apache.ibatis.session.Configuration;
import org.flywaydb.core.Flyway;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Production SQL, schema migrations and transactional publication against an isolated MySQL. */
final class TranscodeFaultDatabase implements AutoCloseable {
    private final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
    final JdbcTemplate jdbc;
    final VideoTranscodeTaskMapper tasks;
    final VideoTranscodeResultService results;
    final String taskId = UUID.randomUUID().toString();
    final String fileMd5 = UUID.randomUUID().toString().replace("-", "");

    TranscodeFaultDatabase(String url, String username, String password, String sourceUrl) throws Exception {
        var source = new DriverManagerDataSource(url, username, password);
        Flyway.configure().dataSource(source).locations("classpath:db/migration").load().migrate();
        jdbc = new JdbcTemplate(source);
        Configuration configuration = new Configuration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(VideoTranscodeTaskMapper.class);
        configuration.addMapper(VideoMapper.class);
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(source);
        factory.setConfiguration(configuration);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        tasks = session.getMapper(VideoTranscodeTaskMapper.class);
        context.register(Transactions.class);
        context.registerBean(PlatformTransactionManager.class, () -> new DataSourceTransactionManager(source));
        context.registerBean(VideoTranscodeTaskMapper.class, () -> tasks);
        context.registerBean(VideoMapper.class, () -> session.getMapper(VideoMapper.class));
        context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
        context.registerBean(VideoTranscodeResultService.class);
        context.refresh();
        results = context.getBean(VideoTranscodeResultService.class);
        jdbc.update("insert into video_transcode_task (task_id,user_id,file_md5,file_name,source_url,source_object_name) "
                + "values (?,1,?,'matrix.mp4',?,'source.mp4')", taskId, fileMd5, sourceUrl);
        jdbc.update("insert into video (user_id,title,file_md5) values (1,'fault matrix',?)", fileMd5);
    }

    VideoTranscodeTask row() { return tasks.selectByTaskId(taskId); }

    VideoTranscodeMessage claim() {
        // Advance retry eligibility only; keep production claim/failure SQL and retry counters intact.
        jdbc.update("update video_transcode_task set next_retry_time=timestampadd(SECOND,-1,now(6)), "
                + "rendition_next_retry_time=timestampadd(SECOND,-1,now(6)) where task_id=?", taskId);
        VideoTranscodeTask before = row();
        String token = UUID.randomUUID().toString();
        assertThat(tasks.markDispatched(taskId, before.getClaimGeneration(), token, 120, 2)).isEqualTo(1);
        VideoTranscodeMessage message = new VideoTranscodeMessage();
        message.setTaskId(taskId);
        message.setClaimGeneration(before.getClaimGeneration() + 1);
        message.setClaimToken(token);
        return message;
    }

    void assertPlaybackReference(String expected) {
        assertThat(row().getOutputUrl()).isEqualTo(expected);
        assertThat(jdbc.queryForObject("select play_url from video where file_md5=?", String.class, fileMd5))
                .isEqualTo(expected);
    }

    @Override
    public void close() {
        context.close();
        jdbc.update("delete from video where file_md5=?", fileMd5);
        jdbc.update("delete from video_transcode_task where task_id=?", taskId);
    }

    @org.springframework.context.annotation.Configuration
    @EnableTransactionManagement
    static class Transactions { }
}
