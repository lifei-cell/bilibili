package com.gary.bilibili.common.reliability;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class MessageOperationsControllerTest {
    private final MessageReplayService replays = mock(MessageReplayService.class);
    private final OutboxOperationsRepository operations = mock(OutboxOperationsRepository.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        var auth = new OperationsAuthorization("test-secret", new MockEnvironment());
        mvc = MockMvcBuilders.standaloneSetup(new OutboxOperationsController(auth, operations, replays),
                        new FailedMessageAdminController(new FailedMessageAdminService(mock(FailedMessageRepository.class), replays), auth, operations))
                .setControllerAdvice(new MessageOperationsErrors()).build();
    }

    @Test
    void allOperationalReadsAndWritesRequireAdminToken() throws Exception {
        for (String path : new String[]{"/outbox/exhausted", "/outbox/archives", "/outbox/1/replays", "/failures/1/replays"}) {
            mvc.perform(get("/api/admin/mq" + path)).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/admin/mq/outbox/1/replay").contentType("application/json")
                .content(body())).andExpect(status().isUnauthorized());
        verifyNoInteractions(replays, operations);
    }

    @Test
    void replayRejectsMissingReasonAndExposesConflictsWithoutDispatchingDirectly() throws Exception {
        mvc.perform(post("/api/admin/mq/outbox/1/replay").header("X-Admin-Token", "test-secret")
                .contentType("application/json")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/mq/outbox/1/replay").header("X-Admin-Token", "test-secret")
                .contentType("application/json").content("{broken")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/mq/outbox/1/replay").header("X-Admin-Token", "test-secret")
                .contentType("application/json").content("{\"requestId\":\"request-test-0001\",\"expectedReplayCount\":0}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(replays);
        mvc.perform(post("/api/admin/mq/outbox/1/replay").header("X-Admin-Token", "test-secret")
                .contentType("application/json").content("{\"requestId\":\"request-test-0001\",\"reason\":\"Dependency repaired and checked\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(replays);
        when(replays.replayOutbox(eq(1L), any())).thenThrow(new IllegalStateException("stale version"));
        mvc.perform(post("/api/admin/mq/outbox/1/replay").header("X-Admin-Token", "test-secret")
                .contentType("application/json").content(body())).andExpect(status().isConflict());
    }

    @Test
    void dlqEndpointUsesSameAuditedReplayContract() throws Exception {
        when(replays.replayFailure(eq(1L), any())).thenReturn(
                new MessageReplayService.ReplayReceipt("request-test-0001", "DLQ", 1, "event-1"));
        mvc.perform(post("/api/admin/mq/failures/1/replay").header("X-Admin-Token", "test-secret")
                .contentType("application/json").content(body())).andExpect(status().isOk());
        verify(replays).replayFailure(1L, new MessageReplayRequest("request-test-0001", 0, "Dependency repaired and checked"));
    }

    private String body() {
        return "{\"requestId\":\"request-test-0001\",\"expectedReplayCount\":0,\"reason\":\"Dependency repaired and checked\"}";
    }
}
