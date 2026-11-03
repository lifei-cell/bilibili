package com.gary.bilibili.common.reliability;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

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
        FailedMessageAdminService service = service(repository);

        FailedMessageAdminService.FailedMessagePage page =
                service.find(" video-view ", "unknown", 0, 200);

        assertThat(page.records()).containsExactly(failed);
        assertThat(page.total()).isEqualTo(1);
    }

    @Test
    void shouldDelegateReplayToDurableAuditedQueue() {
        FailedMessageRepository repository = mock(FailedMessageRepository.class);
        MessageReplayService replays = mock(MessageReplayService.class);
        var request = new MessageReplayRequest("request-00000001", 0, "Fixed unavailable dependency");
        var receipt = new MessageReplayService.ReplayReceipt(request.requestId(), "DLQ", 7, "queued-event");
        when(replays.replayFailure(7, request)).thenReturn(receipt);
        var service = new FailedMessageAdminService(repository, replays);
        assertThat(service.replay(7, request)).isEqualTo(receipt);
        verify(replays).replayFailure(7, request);
    }

    private FailedMessageAdminService service(FailedMessageRepository repository) {
        return new FailedMessageAdminService(repository, mock(MessageReplayService.class));
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
