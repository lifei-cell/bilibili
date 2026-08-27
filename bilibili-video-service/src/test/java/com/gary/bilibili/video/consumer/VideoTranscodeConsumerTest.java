package com.gary.bilibili.video.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.message.VideoTranscodeMessage;
import com.gary.bilibili.video.model.MediaTranscodeResult;
import com.gary.bilibili.video.service.VideoTranscodeWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VideoTranscodeConsumerTest {

    private VideoTranscodeTaskMapper taskMapper;
    private VideoMapper videoMapper;
    private VideoTranscodeWorker worker;
    private VideoTranscodeConsumer consumer;
    private VideoTranscodeTask task;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        taskMapper = mock(VideoTranscodeTaskMapper.class);
        videoMapper = mock(VideoMapper.class);
        worker = mock(VideoTranscodeWorker.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        task = new VideoTranscodeTask();
        task.setTaskId("task-1");
        task.setFileMd5("md5-1");
        task.setStatus(1);
        when(taskMapper.selectByTaskId("task-1")).thenReturn(task);
        when(taskMapper.markProcessing("task-1")).thenReturn(1);

        consumer = new VideoTranscodeConsumer(
                taskMapper, videoMapper, redisTemplate, worker, new ObjectMapper(), 3, 10, 1860);
    }

    @Test
    void shouldPublishLowRenditionBeforeFinalAdaptiveResult() {
        MediaTranscodeResult playable = result("360P");
        MediaTranscodeResult adaptive = result("360P", "720P", "1080P");
        when(worker.transcode(eq(task), any())).thenAnswer(invocation -> {
            Consumer<MediaTranscodeResult> listener = invocation.getArgument(1);
            listener.accept(playable);
            return adaptive;
        });

        consumer.onMessage(message());

        verify(taskMapper, times(2)).markSuccess(eq("task-1"), anyString(), anyString(), anyString());
        verify(videoMapper, times(2)).updateMediaByFileMd5(eq("md5-1"), anyString(), anyString());
    }

    @Test
    void shouldKeepPlayableResultWhenHigherRenditionsFail() {
        MediaTranscodeResult playable = result("360P");
        when(worker.transcode(eq(task), any())).thenAnswer(invocation -> {
            Consumer<MediaTranscodeResult> listener = invocation.getArgument(1);
            listener.accept(playable);
            throw new IllegalStateException("1080P encoder unavailable");
        });

        consumer.onMessage(message());

        verify(taskMapper).markDegradedSuccess(eq("task-1"), anyString());
        verify(taskMapper, never()).markPendingAfterFailure(anyString(), anyString(), any(Integer.class), any(Integer.class));
        verify(videoMapper).updateMediaByFileMd5(eq("md5-1"), anyString(), anyString());
    }

    private VideoTranscodeMessage message() {
        VideoTranscodeMessage message = new VideoTranscodeMessage();
        message.setTaskId("task-1");
        return message;
    }

    private MediaTranscodeResult result(String... qualities) {
        List<MediaTranscodeResult.Variant> variants = java.util.Arrays.stream(qualities)
                .map(quality -> new MediaTranscodeResult.Variant(
                        quality, 640, 360, 800_000, "http://media/" + quality + "/index.m3u8"))
                .toList();
        return new MediaTranscodeResult("http://media/master.m3u8", "http://media/cover.jpg", variants);
    }
}
