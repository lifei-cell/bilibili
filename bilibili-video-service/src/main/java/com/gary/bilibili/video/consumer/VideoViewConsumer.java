package com.gary.bilibili.video.consumer;

import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
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

    private static final String CONSUMER_GROUP = "video-view-consumer";

    private static final DefaultRedisScript<Long> INCREMENT_VIEW_SCRIPT = new DefaultRedisScript<>(
            "if ARGV[2] ~= '' then "
                    + "if redis.call('SETNX', KEYS[3], '1') == 0 then return -1 end; "
                    + "redis.call('EXPIRE', KEYS[3], ARGV[3]); end; "
                    + "local count = redis.call('HINCRBY', KEYS[1], ARGV[1], 1); "
                    + "redis.call('SADD', KEYS[2], ARGV[4]); return count;",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;
    private final ReliableMessageExecutor reliableMessageExecutor;

    public VideoViewConsumer(StringRedisTemplate stringRedisTemplate,
                             ReliableMessageExecutor reliableMessageExecutor) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.reliableMessageExecutor = reliableMessageExecutor;
    }

    @Override
    public void onMessage(VideoViewMessage message) {
        if (message == null || message.getVideoId() == null || message.getVideoId() <= 0) {
            return;
        }
        reliableMessageExecutor.execute(
                VideoConstant.VIEW_TOPIC,
                CONSUMER_GROUP,
                message.getVideoId() + ":" + message.getRequestId(),
                message,
                () -> stringRedisTemplate.execute(
                        INCREMENT_VIEW_SCRIPT,
                        List.of(VideoConstant.STATS_CACHE_KEY_PREFIX + message.getVideoId(),
                                VideoConstant.STATS_PENDING_KEY,
                                VideoConstant.VIEW_REQUEST_DEDUP_KEY_PREFIX + message.getVideoId()
                                        + ":" + message.getRequestId()),
                        VideoConstant.VIEW_COUNT_FIELD,
                        message.getRequestId() == null ? "" : message.getRequestId(),
                        Integer.toString(VideoConstant.VIEW_REQUEST_DEDUP_TTL_SECONDS),
                        message.getVideoId().toString()));
    }
}
