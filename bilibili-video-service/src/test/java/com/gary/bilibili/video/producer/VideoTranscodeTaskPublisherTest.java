package com.gary.bilibili.video.producer;

import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoTranscodeTaskPublisherTest {

    @SuppressWarnings("unchecked")
    @Test
    void shouldPublishOutboxTaskWithStableTaskId() {
        VideoTranscodeTaskMapper taskMapper = mock(VideoTranscodeTaskMapper.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any())).thenReturn(true);

        VideoTranscodeTask task = new VideoTranscodeTask();
        task.setTaskId("upload-task-001");
        task.setUserId(1001L);
        task.setFileMd5("79b281060d337b9b2b84ccf390adcf74");
        task.setFileName("demo.mp4");
        task.setFileSize(1024L);
        task.setSourceUrl("http://minio/videos/source/demo.mp4");
        task.setClaimGeneration(0L);
        when(taskMapper.selectDispatchable(20)).thenReturn(List.of(task));
        when(taskMapper.markDispatched(eq("upload-task-001"), eq(0L), anyString(), eq(120))).thenReturn(1);

        VideoTranscodeTaskPublisher publisher = new VideoTranscodeTaskPublisher(
                taskMapper, rocketMQTemplate, redisTemplate, 3, 10, 120);
        publisher.dispatchPendingTasks();

        verify(rocketMQTemplate).convertAndSend(
                eq("video-transcode"),
                (Object) org.mockito.ArgumentMatchers.argThat(message ->
                        message instanceof com.gary.bilibili.video.message.VideoTranscodeMessage
                                && "upload-task-001".equals(
                                ((com.gary.bilibili.video.message.VideoTranscodeMessage) message).getTaskId())
                                && Long.valueOf(1).equals(
                                ((com.gary.bilibili.video.message.VideoTranscodeMessage) message).getClaimGeneration())
                                && ((com.gary.bilibili.video.message.VideoTranscodeMessage) message).getClaimToken() != null));
    }
}
