package com.gary.bilibili.common.analytics;

import com.gary.bilibili.common.reliability.OutboxEventService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class CreatorAnalyticsDirtyPublisherTest {

    @Test
    void shouldAppendTransactionalDirtyEvent() {
        OutboxEventService outbox = mock(OutboxEventService.class);
        CreatorAnalyticsDirtyPublisher publisher = new CreatorAnalyticsDirtyPublisher(outbox);
        LocalDate day = LocalDate.of(2026, 9, 20);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        publisher.publish(101L, day);

        verify(outbox).append(
                anyString(), eq("creator-analytics"), eq("101"), eq("CREATOR_ANALYTICS_DAY_DIRTY"),
                eq(CreatorAnalyticsDirtyMessage.TOPIC), payload.capture());
        assertThat(payload.getValue()).isInstanceOfSatisfying(
                CreatorAnalyticsDirtyMessage.class,
                message -> {
                    assertThat(message.eventId()).isNotBlank();
                    assertThat(message.videoId()).isEqualTo(101L);
                    assertThat(message.statDate()).isEqualTo(day);
                });
    }

    @Test
    void shouldAppendStandaloneDirtyEventWithFallbackDate() {
        OutboxEventService outbox = mock(OutboxEventService.class);
        CreatorAnalyticsDirtyPublisher publisher = new CreatorAnalyticsDirtyPublisher(outbox);
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);

        publisher.publishStandalone(102L, null);

        verify(outbox).appendStandalone(
                anyString(), eq("creator-analytics"), eq("102"), eq("CREATOR_ANALYTICS_DAY_DIRTY"),
                eq(CreatorAnalyticsDirtyMessage.TOPIC), payload.capture());
        assertThat(payload.getValue()).isInstanceOfSatisfying(
                CreatorAnalyticsDirtyMessage.class,
                message -> assertThat(message.statDate()).isEqualTo(LocalDate.now()));
    }
}
