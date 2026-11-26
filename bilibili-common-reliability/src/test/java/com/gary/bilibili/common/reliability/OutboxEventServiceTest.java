package com.gary.bilibili.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class OutboxEventServiceTest {

    @Test
    void shouldPersistSerializedEventWithOwningService() {
        OutboxEventRepository repository = mock(OutboxEventRepository.class);
        OutboxEventService service = new OutboxEventService(
                repository, new ObjectMapper(), "bilibili-video-service");
        TestEvent event = new TestEvent("request-1", 1001L);

        String eventId = service.appendStandalone(
                "video", "1001", "VIDEO_VIEWED", "video-view", event);

        assertThat(eventId).isNotBlank();
        verify(repository).insert(eq(eventId), eq("bilibili-video-service"),
                eq("video"), eq("1001"), eq("VIDEO_VIEWED"), eq("video-view"),
                eq(TestEvent.class.getName()), eq("{\"requestId\":\"request-1\",\"videoId\":1001}"));
    }

    private record TestEvent(String requestId, Long videoId) {
    }
}
