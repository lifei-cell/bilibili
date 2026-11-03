package com.gary.bilibili.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

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
        MessageInboxLease lease = new MessageInboxLease("video-view", "video-view-consumer", "message-1", 1, "owner-1");
        when(inboxRepository.acquire("video-view", "video-view-consumer", "message-1", 60))
                .thenReturn(Optional.of(lease));
        when(inboxRepository.markFailed(eq(lease), any())).thenReturn(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliableMessageExecutor executor = new ReliableMessageExecutor(
                repository, inboxRepository, passthroughGuard(), new ObjectMapper(), registry,
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
                eq(lease),
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
        MessageInboxLease lease = new MessageInboxLease("danmu-persist", "danmu-persist-consumer", "1", 1, "owner-1");
        when(inboxRepository.acquire("danmu-persist", "danmu-persist-consumer", "1", 60))
                .thenReturn(Optional.of(lease));
        when(inboxRepository.markSucceeded(lease)).thenReturn(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliableMessageExecutor executor = new ReliableMessageExecutor(
                repository, inboxRepository, passthroughGuard(), new ObjectMapper(), registry,
                3, Duration.ZERO, 60, true);
        Runnable action = mock(Runnable.class);

        executor.execute("danmu-persist", "danmu-persist-consumer", "1",
                new TestPayload("1"), action);

        verify(action).run();
        verify(inboxRepository).markSucceeded(lease);
        verify(repository).markResolved(
                "danmu-persist", "danmu-persist-consumer", "1", "{\"id\":\"1\"}");
        verify(repository, never()).saveFailure(any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldSkipAlreadyConsumedMessage() {
        FailedMessageRepository repository = mock(FailedMessageRepository.class);
        MessageInboxRepository inboxRepository = mock(MessageInboxRepository.class);
        when(inboxRepository.acquire(any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        when(inboxRepository.isSucceeded("cache-sync", "cdc-consumer", "binlog:1")).thenReturn(true);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ReliableMessageExecutor executor = new ReliableMessageExecutor(
                repository, inboxRepository, passthroughGuard(), new ObjectMapper(), registry,
                3, Duration.ZERO, 60, true);
        Runnable action = mock(Runnable.class);

        executor.execute("cache-sync", "cdc-consumer", "binlog:1",
                new TestPayload("binlog:1"), action);

        verify(action, never()).run();
        assertThat(registry.get("bilibili.mq.consume")
                .tag("result", "duplicate").counter().count()).isEqualTo(1);
    }

    @Test
    void shouldRedeliverBusyMessageEvenWhenDlqRethrowIsDisabled() {
        FailedMessageRepository failures = mock(FailedMessageRepository.class);
        MessageInboxRepository inbox = mock(MessageInboxRepository.class);
        when(inbox.acquire(any(), any(), any(), anyInt())).thenReturn(Optional.empty());
        Runnable action = mock(Runnable.class);
        ReliableMessageExecutor executor = new ReliableMessageExecutor(failures, inbox, passthroughGuard(),
                new ObjectMapper(), new SimpleMeterRegistry(), 3, Duration.ZERO, 60, false);

        assertThatThrownBy(() -> executor.execute("topic", "consumer", "1", new TestPayload("1"), action))
                .isInstanceOf(ReliableMessageExecutor.MessageProcessingException.class)
                .hasMessageContaining("still processing");
        verify(action, never()).run();
    }

    @Test
    void shouldNotRunStaleActionOrTouchDlq() {
        FailedMessageRepository failures = mock(FailedMessageRepository.class);
        MessageInboxRepository inbox = mock(MessageInboxRepository.class);
        MessageInboxLease lease = new MessageInboxLease("topic", "consumer", "1", 1, "old-owner");
        when(inbox.acquire(any(), any(), any(), anyInt())).thenReturn(Optional.of(lease));
        MessageInboxLeaseGuard guard = mock(MessageInboxLeaseGuard.class);
        org.mockito.Mockito.doThrow(new MessageInboxLeaseLostException(lease))
                .when(guard).run(eq(lease), any(Runnable.class));
        Runnable action = mock(Runnable.class);
        ReliableMessageExecutor executor = new ReliableMessageExecutor(failures, inbox, guard,
                new ObjectMapper(), new SimpleMeterRegistry(), 3, Duration.ZERO, 60, true);

        assertThatThrownBy(() -> executor.execute("topic", "consumer", "1", new TestPayload("1"), action))
                .isInstanceOf(MessageInboxLeaseLostException.class);
        verify(action, never()).run();
        verify(inbox, never()).markSucceeded(any());
        verify(inbox, never()).markFailed(any(), any());
        verify(failures, never()).markResolved(any(), any(), any(), any());
        verify(failures, never()).saveFailure(any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldNotRetryOrResolveDlqWhenCompletionLosesLease() {
        FailedMessageRepository failures = mock(FailedMessageRepository.class);
        MessageInboxRepository inbox = mock(MessageInboxRepository.class);
        MessageInboxLease lease = new MessageInboxLease("topic", "consumer", "1", 1, "old-owner");
        when(inbox.acquire(any(), any(), any(), anyInt())).thenReturn(Optional.of(lease));
        Runnable action = mock(Runnable.class);
        ReliableMessageExecutor executor = new ReliableMessageExecutor(failures, inbox, passthroughGuard(),
                new ObjectMapper(), new SimpleMeterRegistry(), 3, Duration.ZERO, 60, true);

        assertThatThrownBy(() -> executor.execute("topic", "consumer", "1", new TestPayload("1"), action))
                .isInstanceOf(MessageInboxLeaseLostException.class);
        verify(action).run();
        verify(inbox, never()).markFailed(any(), any());
        verify(failures, never()).markResolved(any(), any(), any(), any());
        verify(failures, never()).saveFailure(any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    @Test
    void shouldNotCreateDlqWhenFinalFailureLosesLease() {
        FailedMessageRepository failures = mock(FailedMessageRepository.class);
        MessageInboxRepository inbox = mock(MessageInboxRepository.class);
        MessageInboxLease lease = new MessageInboxLease("topic", "consumer", "1", 1, "old-owner");
        when(inbox.acquire(any(), any(), any(), anyInt())).thenReturn(Optional.of(lease));
        Runnable action = () -> { throw new IllegalStateException("business failure"); };
        ReliableMessageExecutor executor = new ReliableMessageExecutor(failures, inbox, passthroughGuard(),
                new ObjectMapper(), new SimpleMeterRegistry(), 3, Duration.ZERO, 60, true);

        assertThatThrownBy(() -> executor.execute("topic", "consumer", "1", new TestPayload("1"), action))
                .isInstanceOf(MessageInboxLeaseLostException.class);
        verify(failures, never()).saveFailure(any(), any(), any(), any(), any(), any(), any(), anyInt());
    }

    private static MessageInboxLeaseGuard passthroughGuard() {
        MessageInboxLeaseGuard guard = mock(MessageInboxLeaseGuard.class);
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.getArgument(1, Runnable.class).run();
            return null;
        }).when(guard).run(any(), any(Runnable.class));
        return guard;
    }

    private record TestPayload(String id) {
    }
}
