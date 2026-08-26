package com.gary.bilibili.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReliableMessageExecutorTest {

    @Test
    void shouldRetryAndPersistFinalFailure() {
        FailedMessageRepository repository = mock(FailedMessageRepository.class);
        MessageInboxRepository inboxRepository = mock(MessageInboxRepository.class);
        when(inboxRepository.acquire("video-view", "video-view-consumer", "message-1", 60))
                .thenReturn(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliableMessageExecutor executor = new ReliableMessageExecutor(
                repository, inboxRepository, new ObjectMapper(), registry,
                3, Duration.ZERO, 60, true);
        Runnable action = mock(Runnable.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("redis unavailable"))
                .when(action).run();
        TestPayload payload = new TestPayload("message-1");

        assertThatThrownBy(() -> executor.execute(
                "video-view", "video-view-consumer", "message-1", payload, action))
                .isInstanceOf(ReliableMessageExecutor.MessageProcessingException.class);

        verify(action, times(3)).run();
        verify(inboxRepository).markFailed(
                eq("video-view"), eq("video-view-consumer"), eq("message-1"),
                any(IllegalStateException.class));
        verify(repository).saveFailure(
                eq("video-view"), eq("video-view-consumer"), eq("message-1"),
                eq(TestPayload.class.getName()), eq("{\"id\":\"message-1\"}"),
                any(IllegalStateException.class), nullable(String.class), eq(3));
        assertThat(registry.get("bilibili.mq.consume")
                .tag("result", "dlq").counter().count()).isEqualTo(1);
    }

    @Test
    void shouldStopAfterSuccessfulAttempt() {
        FailedMessageRepository repository = mock(FailedMessageRepository.class);
        MessageInboxRepository inboxRepository = mock(MessageInboxRepository.class);
        when(inboxRepository.acquire("danmu-persist", "danmu-persist-consumer", "1", 60))
                .thenReturn(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliableMessageExecutor executor = new ReliableMessageExecutor(
                repository, inboxRepository, new ObjectMapper(), registry,
                3, Duration.ZERO, 60, true);
        Runnable action = mock(Runnable.class);

        executor.execute("danmu-persist", "danmu-persist-consumer", "1",
                new TestPayload("1"), action);

        verify(action).run();
        verify(inboxRepository).markSucceeded(
                "danmu-persist", "danmu-persist-consumer", "1");
        verify(repository).markResolved(
                "danmu-persist", "danmu-persist-consumer", "1", "{\"id\":\"1\"}");
        verify(repository, never()).saveFailure(any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldSkipAlreadyConsumedMessage() {
        FailedMessageRepository repository = mock(FailedMessageRepository.class);
        MessageInboxRepository inboxRepository = mock(MessageInboxRepository.class);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliableMessageExecutor executor = new ReliableMessageExecutor(
                repository, inboxRepository, new ObjectMapper(), registry,
                3, Duration.ZERO, 60, true);
        Runnable action = mock(Runnable.class);

        executor.execute("cache-sync", "cdc-consumer", "binlog:1",
                new TestPayload("binlog:1"), action);

        verify(action, never()).run();
        assertThat(registry.get("bilibili.mq.consume")
                .tag("result", "duplicate").counter().count()).isEqualTo(1);
    }

    private record TestPayload(String id) {
    }
}
