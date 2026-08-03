package com.gary.bilibili.danmu.service;

import com.gary.bilibili.danmu.mapper.DanmuMapper;
import com.gary.bilibili.danmu.message.DanmuPersistMessage;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DanmuBatchPersistServiceTest {

    @Test
    void shouldBatchPersistQueuedRocketMqMessages() throws Exception {
        DanmuMapper danmuMapper = mock(DanmuMapper.class);
        when(danmuMapper.insertBatch(anyList())).thenReturn(1);
        DanmuBatchPersistService persistService =
                new DanmuBatchPersistService(danmuMapper, 100);

        DanmuPersistMessage message = new DanmuPersistMessage();
        message.setId(90001L);
        CompletableFuture<Void> persisted = CompletableFuture.runAsync(
                () -> persistService.persist(message));
        for (int i = 0; i < 100 && !persisted.isDone(); i++) {
            persistService.flush();
            TimeUnit.MILLISECONDS.sleep(10);
        }
        persisted.get(1, TimeUnit.SECONDS);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<DanmuPersistMessage>> captor =
                org.mockito.ArgumentCaptor.forClass(List.class);
        verify(danmuMapper).insertBatch(captor.capture());
        assertThat(captor.getValue()).singleElement()
                .satisfies(item -> assertThat(item.getId()).isEqualTo(90001L));
    }
}
