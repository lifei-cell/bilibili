package com.gary.bilibili.common.analytics;

import com.gary.bilibili.common.reliability.OutboxEventService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.UUID;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class CreatorAnalyticsDirtyPublisher {

    private final OutboxEventService outboxEventService;

    public CreatorAnalyticsDirtyPublisher(OutboxEventService outboxEventService) {
        this.outboxEventService = outboxEventService;
    }

    public void publish(long videoId, LocalDate statDate) {
        String eventId = UUID.randomUUID().toString();
        outboxEventService.append(
                eventId, "creator-analytics", Long.toString(videoId), "CREATOR_ANALYTICS_DAY_DIRTY",
                CreatorAnalyticsDirtyMessage.TOPIC,
                new CreatorAnalyticsDirtyMessage(eventId, videoId, normalize(statDate)));
    }

    public void publishStandalone(long videoId, LocalDate statDate) {
        String eventId = UUID.randomUUID().toString();
        outboxEventService.appendStandalone(
                eventId, "creator-analytics", Long.toString(videoId), "CREATOR_ANALYTICS_DAY_DIRTY",
                CreatorAnalyticsDirtyMessage.TOPIC,
                new CreatorAnalyticsDirtyMessage(eventId, videoId, normalize(statDate)));
    }

    private LocalDate normalize(LocalDate statDate) {
        return statDate == null ? LocalDate.now() : statDate;
    }
}
