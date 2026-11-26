package com.gary.bilibili.danmu.consumer;

import com.gary.bilibili.common.analytics.CreatorAnalyticsDirtyPublisher;
import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.danmu.message.DanmuPersistMessage;
import com.gary.bilibili.danmu.service.DanmuBatchPersistService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DanmuPersistConsumerTest {

    @Test
    void shouldDelegatePersistenceToReliableExecutor() {
        DanmuBatchPersistService persistService = mock(DanmuBatchPersistService.class);
        ReliableMessageExecutor executor = mock(ReliableMessageExecutor.class);
        CreatorAnalyticsDirtyPublisher publisher = mock(CreatorAnalyticsDirtyPublisher.class);
        DanmuPersistConsumer consumer = new DanmuPersistConsumer(
                persistService, executor, publisher);
        DanmuPersistMessage message = new DanmuPersistMessage();
        message.setId(1001L);
        message.setVideoId(2001L);
        message.setSendTime(LocalDateTime.of(2026, 9, 22, 12, 0));
        doAnswer(invocation -> {
            invocation.getArgument(4, Runnable.class).run();
            return null;
        }).when(executor).execute(any(), any(), any(), any(), any(Runnable.class));

        consumer.onMessage(message);

        verify(executor).execute(
                eq("danmu-persist"), eq("danmu-persist-consumer"), eq("1001"),
                eq(message), any(Runnable.class));
        verify(persistService).persist(message);
        verify(publisher).publishStandalone(
                2001L, LocalDateTime.of(2026, 9, 22, 12, 0).toLocalDate());
    }
}
