package com.gary.bilibili.video.consumer;

import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.video.constant.VideoConstant;
import com.gary.bilibili.video.message.VideoViewMessage;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

class VideoViewConsumerTest {

    @Test
    void shouldPassRequestIdAndDedupKeyToAtomicScript() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        ReliableMessageExecutor executor = mock(ReliableMessageExecutor.class);
        doAnswer(invocation -> {
            invocation.<Runnable>getArgument(4).run();
            return null;
        }).when(executor).execute(any(), any(), any(), any(), any());
        VideoViewConsumer consumer = new VideoViewConsumer(redisTemplate, executor);
        VideoViewMessage message = new VideoViewMessage();
        message.setVideoId(10001L);
        message.setRequestId("view-request-001");

        consumer.onMessage(message);

        verify(redisTemplate).execute(
                any(RedisScript.class),
                eq(List.of(
                        VideoConstant.STATS_CACHE_KEY_PREFIX + "10001",
                        VideoConstant.STATS_PENDING_KEY,
                        VideoConstant.VIEW_REQUEST_DEDUP_KEY_PREFIX + "10001:view-request-001")),
                eq(VideoConstant.VIEW_COUNT_FIELD),
                eq("view-request-001"),
                eq(Integer.toString(VideoConstant.VIEW_REQUEST_DEDUP_TTL_SECONDS)),
                eq("10001"));
    }
}
