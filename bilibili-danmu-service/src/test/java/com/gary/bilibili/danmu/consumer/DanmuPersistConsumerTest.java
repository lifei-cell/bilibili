package com.gary.bilibili.danmu.consumer;

import com.gary.bilibili.common.reliability.ReliableMessageExecutor;
import com.gary.bilibili.danmu.message.DanmuPersistMessage;
import com.gary.bilibili.danmu.service.DanmuBatchPersistService;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DanmuPersistConsumerTest {

    @Test
    void shouldDelegatePersistenceToReliableExecutor() {
        DanmuBatchPersistService persistService = mock(DanmuBatchPersistService.class);
        ReliableMessageExecutor executor = mock(ReliableMessageExecutor.class);
        DanmuPersistConsumer consumer = new DanmuPersistConsumer(persistService, executor);
        DanmuPersistMessage message = new DanmuPersistMessage();
        message.setId(1001L);

        consumer.onMessage(message);

        verify(executor).execute(
                eq("danmu-persist"), eq("danmu-persist-consumer"), eq("1001"),
                eq(message), any(Runnable.class));
    }
}
