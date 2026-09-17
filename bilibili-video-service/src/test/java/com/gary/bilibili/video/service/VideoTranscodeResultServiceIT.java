package com.gary.bilibili.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.model.MediaTranscodeResult;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VideoTranscodeResultServiceIT {

    @Test
    void secondWriteFailureRollsBackTaskAndRetryUpdatesPlayback() {
        {
            DataSource dataSource = new DriverManagerDataSource(
                    "jdbc:h2:mem:transcode-result;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
            JdbcTemplate jdbc = new JdbcTemplate(dataSource);
            jdbc.execute("create table video_transcode_task (task_id varchar(64) primary key, "
                    + "status int not null, output_url varchar(500), cover_url varchar(500), "
                    + "variants_json json, claim_generation bigint not null, claim_token varchar(64), "
                    + "lease_until timestamp)");
            jdbc.execute("create table video (id bigint primary key, file_md5 varchar(64) not null, "
                    + "play_url varchar(500), cover_url varchar(500), deleted int not null)");
            jdbc.update("insert into video_transcode_task "
                    + "(task_id, status, claim_generation, claim_token, lease_until) "
                    + "values (?, ?, ?, ?, dateadd('hour', 1, current_timestamp))",
                    "task-1", 2, 1L, "owner-1");
            jdbc.update("insert into video (id, file_md5, deleted) values (?, ?, ?)", 1L, "md5-1", 0);

            VideoTranscodeTaskMapper taskMapper = mock(VideoTranscodeTaskMapper.class);
            when(taskMapper.markSuccess(anyString(), anyLong(), anyString(), anyString(),
                    anyString(), anyString(), anyBoolean()))
                    .thenAnswer(call -> jdbc.update("update video_transcode_task "
                                    + "set status = 3, output_url = ?, cover_url = ?, variants_json = ? "
                                    + "where task_id = ? and claim_generation = ? and claim_token = ? "
                                    + "and lease_until > current_timestamp and status in (2, 3)",
                            call.getArgument(3), call.getArgument(4), call.getArgument(5),
                            call.getArgument(0), call.getArgument(1), call.getArgument(2)));
            VideoMapper videoMapper = mock(VideoMapper.class);
            AtomicBoolean failVideoWrite = new AtomicBoolean(true);
            when(videoMapper.updateMediaByFileMd5(anyString(), anyString(), anyString()))
                    .thenAnswer(call -> {
                        if (failVideoWrite.get()) {
                            throw new IllegalStateException("injected video update failure");
                        }
                        return jdbc.update("update video set play_url = ?, cover_url = ? "
                                        + "where file_md5 = ? and deleted = 0",
                                call.getArgument(1), call.getArgument(2), call.getArgument(0));
                    });

            try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
                context.register(TransactionConfig.class);
                context.registerBean(DataSource.class, () -> dataSource);
                context.registerBean(PlatformTransactionManager.class,
                        () -> new DataSourceTransactionManager(dataSource));
                context.registerBean(VideoTranscodeTaskMapper.class, () -> taskMapper);
                context.registerBean(VideoMapper.class, () -> videoMapper);
                context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
                context.registerBean(VideoTranscodeResultService.class);
                context.refresh();

                VideoTranscodeResultService service = context.getBean(VideoTranscodeResultService.class);
                VideoTranscodeTask task = new VideoTranscodeTask();
                task.setTaskId("task-1");
                task.setFileMd5("md5-1");
                task.setClaimGeneration(1L);
                task.setClaimToken("owner-1");
                MediaTranscodeResult result = new MediaTranscodeResult(
                        "http://media/master.m3u8", "http://media/cover.jpg", List.of());

                assertThatThrownBy(() -> service.publish(task, result, true))
                        .isInstanceOf(IllegalStateException.class)
                        .hasMessageContaining("injected video update failure");
                assertThat(jdbc.queryForObject(
                        "select status from video_transcode_task where task_id = 'task-1'", Integer.class))
                        .isEqualTo(2);
                assertThat(jdbc.queryForObject(
                        "select output_url from video_transcode_task where task_id = 'task-1'", String.class))
                        .isNull();
                assertThat(jdbc.queryForObject("select play_url from video where id = 1", String.class))
                        .isNull();

                failVideoWrite.set(false);
                service.publish(task, result, true);
                assertThat(jdbc.queryForObject(
                        "select status from video_transcode_task where task_id = 'task-1'", Integer.class))
                        .isEqualTo(3);
                assertThat(jdbc.queryForObject("select play_url from video where id = 1", String.class))
                        .isEqualTo(result.masterUrl());

                // Reclaim after a timeout: a new holder has generation 2 and distinct objects.
                jdbc.update("update video_transcode_task set status = 2, claim_generation = 2, "
                        + "claim_token = 'owner-2', lease_until = dateadd('hour', 1, current_timestamp), "
                        + "output_url = 'http://media/attempt-2/master.m3u8' where task_id = 'task-1'");
                jdbc.update("update video set play_url = 'http://media/attempt-2/master.m3u8' where id = 1");
                assertThatThrownBy(() -> service.publish(task, result, true))
                        .isInstanceOf(VideoTranscodeLeaseService.LeaseLostException.class);
                assertThat(jdbc.queryForObject("select output_url from video_transcode_task where task_id = 'task-1'",
                        String.class)).isEqualTo("http://media/attempt-2/master.m3u8");
                assertThat(jdbc.queryForObject("select play_url from video where id = 1", String.class))
                        .isEqualTo("http://media/attempt-2/master.m3u8");
            }
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TransactionConfig {
    }
}
