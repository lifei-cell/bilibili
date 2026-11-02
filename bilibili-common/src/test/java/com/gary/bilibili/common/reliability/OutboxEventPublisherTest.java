package com.gary.bilibili.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxEventPublisherTest {
    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void countsOnlyCompletionAcceptedByCurrentLease(boolean accepted) {
        var repository = mock(OutboxEventRepository.class);
        var mq = mock(RocketMQTemplate.class);
        var meters = new SimpleMeterRegistry();
        var lease = new OutboxEventLease(1, "claim");
        when(repository.findDispatchable("test", 50, 60, 100)).thenReturn(List.of(event()));
        when(repository.claim(1, 60, 100)).thenReturn(Optional.of(lease));
        when(repository.markPublished(lease)).thenReturn(accepted);
        publisher(repository, mq, meters).dispatch();
        verify(repository).markPublished(lease);
        assertThat(meters.get("bilibili.outbox.publish").tag("result", "success").counter().count())
                .isEqualTo(accepted ? 1 : 0);
        verify(repository, never()).markFailed(any(), anyString(), any());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void failedSendUsesClaimAndDoesNotCountStaleFailure(boolean accepted) {
        var repository = mock(OutboxEventRepository.class);
        var mq = mock(RocketMQTemplate.class);
        var meters = new SimpleMeterRegistry();
        var lease = new OutboxEventLease(1, "claim");
        when(repository.findDispatchable("test", 50, 60, 100)).thenReturn(List.of(event()));
        when(repository.claim(1, 60, 100)).thenReturn(Optional.of(lease));
        when(mq.syncSend(eq("test-topic"), any(Object.class), eq(3000L)))
                .thenThrow(new IllegalStateException("broker unavailable"));
        when(repository.markFailed(eq(lease), anyString(), any())).thenReturn(accepted);
        publisher(repository, mq, meters).dispatch();
        verify(repository).markFailed(eq(lease), eq("broker unavailable"), any());
        assertThat(meters.get("bilibili.outbox.publish").tag("result", "failure").counter().count())
                .isEqualTo(accepted ? 1 : 0);
        verify(repository, never()).markPublished(any());
    }

    private OutboxEventPublisher publisher(OutboxEventRepository repository, RocketMQTemplate mq,
                                           SimpleMeterRegistry meters) {
        return new OutboxEventPublisher(repository, new ObjectMapper(), mq, meters, "test", 50, 100, 60, 2);
    }

    private OutboxEvent event() {
        return new OutboxEvent(1, "event", "test", "video", "1", "VIEW", "test-topic",
                Payload.class.getName(), "{\"id\":1}", 0, LocalDateTime.now());
    }

    public record Payload(long id) { }
}
