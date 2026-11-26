package com.gary.bilibili.video.consumer;

import com.gary.bilibili.common.analytics.CreatorAnalyticsDirtyMessage;
import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.video.service.CreatorAnalyticsEventService;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CreatorAnalyticsDirtyConsumerTest {

    @Test
    void shouldIdempotentlyMarkTheSourceDayDirty() {
        ReliableMessageExecutor executor = mock(ReliableMessageExecutor.class);
        CreatorAnalyticsEventService eventService = mock(CreatorAnalyticsEventService.class);
        CreatorAnalyticsDirtyConsumer consumer =
                new CreatorAnalyticsDirtyConsumer(executor, eventService);
        LocalDate day = LocalDate.of(2026, 9, 20);
        CreatorAnalyticsDirtyMessage message =
                new CreatorAnalyticsDirtyMessage("event-1", 101L, day);
        doAnswer(invocation -> {
            invocation.getArgument(4, Runnable.class).run();
            return null;
        }).when(executor).execute(any(), any(), any(), any(), any(Runnable.class));

        consumer.onMessage(message);

        verify(executor).execute(
                eq(CreatorAnalyticsDirtyMessage.TOPIC),
                eq("creator-analytics-dirty-consumer"),
                eq("event-1"), eq(message), any(Runnable.class));
        verify(eventService).markDirty(101L, day);
    }

    @Test
    void shouldIgnoreIncompleteMessage() {
        ReliableMessageExecutor executor = mock(ReliableMessageExecutor.class);
        CreatorAnalyticsEventService eventService = mock(CreatorAnalyticsEventService.class);
        CreatorAnalyticsDirtyConsumer consumer =
                new CreatorAnalyticsDirtyConsumer(executor, eventService);

        consumer.onMessage(new CreatorAnalyticsDirtyMessage(null, 101L, LocalDate.now()));

        verify(executor, never()).execute(any(), any(), any(), any(), any(Runnable.class));
    }
}
