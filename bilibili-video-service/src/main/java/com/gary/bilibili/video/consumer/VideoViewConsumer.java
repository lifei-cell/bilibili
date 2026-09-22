package com.gary.bilibili.video.consumer;

import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.video.constant.VideoConstant;
import com.gary.bilibili.video.message.VideoViewMessage;
import com.gary.bilibili.video.service.CreatorAnalyticsEventService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.time.LocalDateTime;

@Service
@ConditionalOnProperty(name = "video.view.consumer-enabled", havingValue = "true", matchIfMissing = true)
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
    private final CreatorAnalyticsEventService analyticsEventService;

    public VideoViewConsumer(StringRedisTemplate stringRedisTemplate,
                             ReliableMessageExecutor reliableMessageExecutor,
                             CreatorAnalyticsEventService analyticsEventService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.reliableMessageExecutor = reliableMessageExecutor;
        this.analyticsEventService = analyticsEventService;
    }

    @Override
    public void onMessage(VideoViewMessage message) {
        if (message == null || message.getVideoId() == null || message.getVideoId() <= 0) {
            return;
        }
        String requestId = normalizeRequestId(message);
        reliableMessageExecutor.execute(
                VideoConstant.VIEW_TOPIC,
                CONSUMER_GROUP,
                message.getVideoId() + ":" + requestId,
                message,
                () -> {
                    analyticsEventService.recordView(
                            requestId, message.getVideoId(), message.getCreateTime());
                    stringRedisTemplate.execute(
                            INCREMENT_VIEW_SCRIPT,
                            List.of(VideoConstant.STATS_CACHE_KEY_PREFIX + message.getVideoId(),
                                    VideoConstant.STATS_PENDING_KEY,
                                    VideoConstant.VIEW_REQUEST_DEDUP_KEY_PREFIX + message.getVideoId()
                                            + ":" + requestId),
                            VideoConstant.VIEW_COUNT_FIELD,
                            requestId,
                            Integer.toString(VideoConstant.VIEW_REQUEST_DEDUP_TTL_SECONDS),
                            message.getVideoId().toString());
                });
    }

    private String normalizeRequestId(VideoViewMessage message) {
        if (message.getRequestId() != null && !message.getRequestId().isBlank()) {
            return message.getRequestId();
        }
        LocalDateTime createTime = message.getCreateTime() == null
                ? LocalDateTime.of(1970, 1, 1, 0, 0) : message.getCreateTime();
        return "legacy-" + message.getVideoId() + "-" + createTime;
    }
}
