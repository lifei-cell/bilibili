package com.gary.bilibili.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FailedMessageAdminServiceTest {

    @Test
    void shouldQueryFailedMessagesWithSafePaging() {
        FailedMessageRepository repository = mock(FailedMessageRepository.class);
        FailedMessage failed = failedMessage();
        when(repository.findFailures("video-view", "FAILED", 0, 100))
                .thenReturn(List.of(failed));
        when(repository.countFailures("video-view", "FAILED")).thenReturn(1L);
        FailedMessageAdminService service = service(repository, null);

        FailedMessageAdminService.FailedMessagePage page =
                service.find(" video-view ", "unknown", 0, 200);

        assertThat(page.records()).containsExactly(failed);
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void shouldReplayStoredPayloadAndMarkItReplayed() {
        FailedMessageRepository repository = mock(FailedMessageRepository.class);
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        FailedMessage failed = failedMessage();
        FailedMessage replayed = new FailedMessage(
                failed.id(), failed.topic(), failed.consumerGroup(), failed.messageKey(),
                failed.payloadType(), failed.payload(), failed.exceptionType(), failed.errorMessage(),
                failed.traceId(), failed.attemptCount(), "REPLAYED", 1,
                failed.createTime(), LocalDateTime.now(), LocalDateTime.now());
        when(repository.findById(7L)).thenReturn(Optional.of(failed), Optional.of(replayed));
        when(repository.markReplayed(7L)).thenReturn(true);
        FailedMessageAdminService service = service(repository, rocketMQTemplate);

        FailedMessage result = service.replay(7L);

        verify(rocketMQTemplate).syncSend("video-view", new TestPayload("message-7"), 3000);
        verify(repository).markReplayed(7L);
        assertThat(result.status()).isEqualTo("REPLAYED");
    }

    @SuppressWarnings("unchecked")
    private FailedMessageAdminService service(FailedMessageRepository repository,
                                               RocketMQTemplate rocketMQTemplate) {
        ObjectProvider<RocketMQTemplate> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(rocketMQTemplate);
        return new FailedMessageAdminService(
                repository, new ObjectMapper(), provider, new SimpleMeterRegistry());
    }

    private FailedMessage failedMessage() {
        return new FailedMessage(
                7L, "video-view", "video-view-consumer", "message-7",
                TestPayload.class.getName(), "{\"id\":\"message-7\"}",
                IllegalStateException.class.getName(), "redis unavailable", "trace-7",
                3, "FAILED", 0, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    private record TestPayload(String id) {
    }
}
