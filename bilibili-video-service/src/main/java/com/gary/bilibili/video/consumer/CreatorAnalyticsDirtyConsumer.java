package com.gary.bilibili.video.consumer;

import com.gary.bilibili.common.analytics.CreatorAnalyticsDirtyMessage;
import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.video.service.CreatorAnalyticsEventService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Service;

@Service
@RocketMQMessageListener(
        topic = CreatorAnalyticsDirtyMessage.TOPIC,
        consumerGroup = "creator-analytics-dirty-consumer")
public class CreatorAnalyticsDirtyConsumer
        implements RocketMQListener<CreatorAnalyticsDirtyMessage> {

    private static final String CONSUMER_GROUP = "creator-analytics-dirty-consumer";

    private final ReliableMessageExecutor reliableMessageExecutor;
    private final CreatorAnalyticsEventService analyticsEventService;

    public CreatorAnalyticsDirtyConsumer(ReliableMessageExecutor reliableMessageExecutor,
                                         CreatorAnalyticsEventService analyticsEventService) {
        this.reliableMessageExecutor = reliableMessageExecutor;
        this.analyticsEventService = analyticsEventService;
    }

    @Override
    public void onMessage(CreatorAnalyticsDirtyMessage message) {
        if (message == null || message.eventId() == null || message.eventId().isBlank()
                || message.videoId() == null || message.videoId() <= 0
                || message.statDate() == null) {
            return;
        }
        reliableMessageExecutor.execute(
                CreatorAnalyticsDirtyMessage.TOPIC,
                CONSUMER_GROUP,
                message.eventId(),
                message,
                () -> analyticsEventService.markDirty(message.videoId(), message.statDate()));
    }
}
