package com.gary.bilibili.common.reliability;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageLifecycleTaskTest {
    @Test
    void failedBatchIsVisibleAndDoesNotReportArchivalProgress() {
        var archive = mock(MessageArchiveRepository.class);
        var meters = new SimpleMeterRegistry();
        when(archive.archivePublished("test", 7, 200)).thenThrow(new IllegalStateException("db unavailable"));
        var task = new MessageLifecycleTask(archive, new MessageLifecyclePolicy(true, 7, 30, 200, 5, 60), meters, "test");
        task.run();
        assertThat(meters.get("bilibili.lifecycle.failures").counter().count()).isEqualTo(1);
        assertThat(meters.get("bilibili.lifecycle.archived").tag("kind", "outbox").counter().count()).isZero();
        verify(archive, never()).compactInbox(30, 200);
    }

    @Test
    void disabledMaintenanceDoesNotDeleteOrArchiveAnything() {
        var archive = mock(MessageArchiveRepository.class);
        new MessageLifecycleTask(archive, new MessageLifecyclePolicy(false, 7, 30, 200, 5, 60),
                new SimpleMeterRegistry(), "test").run();
        verifyNoInteractions(archive);
        assertThatThrownBy(() -> new MessageLifecyclePolicy(true, 0, 30, 200, 5, 60)).isInstanceOf(IllegalArgumentException.class);
    }
}
