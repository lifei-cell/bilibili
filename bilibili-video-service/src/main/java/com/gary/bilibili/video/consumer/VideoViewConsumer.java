package com.gary.bilibili.video.consumer;

import com.gary.bilibili.video.constant.VideoConstant;
import com.gary.bilibili.video.message.VideoViewMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RocketMQMessageListener(
        topic = VideoConstant.VIEW_TOPIC,
        consumerGroup = "video-view-consumer")
public class VideoViewConsumer implements RocketMQListener<VideoViewMessage> {

    private static final DefaultRedisScript<Long> INCREMENT_VIEW_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('HINCRBY', KEYS[1], ARGV[1], 1); "
                    + "redis.call('SADD', KEYS[2], ARGV[2]); return count;",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public VideoViewConsumer(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public void onMessage(VideoViewMessage message) {
        if (message == null || message.getVideoId() == null || message.getVideoId() <= 0) {
            return;
        }
        stringRedisTemplate.execute(
                INCREMENT_VIEW_SCRIPT,
                List.of(VideoConstant.STATS_CACHE_KEY_PREFIX + message.getVideoId(),
                        VideoConstant.STATS_PENDING_KEY),
                VideoConstant.VIEW_COUNT_FIELD,
                message.getVideoId().toString());
    }
}
