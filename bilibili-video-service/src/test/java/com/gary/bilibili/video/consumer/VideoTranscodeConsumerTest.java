package com.gary.bilibili.video.consumer;

import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.message.VideoTranscodeMessage;
import com.gary.bilibili.video.model.MediaTranscodeResult;
import com.gary.bilibili.video.service.VideoTranscodeLeaseService;
import com.gary.bilibili.video.service.VideoTranscodeResultService;
import com.gary.bilibili.video.service.VideoTranscodeWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class VideoTranscodeConsumerTest {

    private VideoTranscodeTaskMapper taskMapper;
    private VideoTranscodeResultService resultService;
    private VideoTranscodeWorker worker;
    private VideoTranscodeConsumer consumer;
    private VideoTranscodeTask task;
    private VideoTranscodeLeaseService.Lease lease;

    @BeforeEach
    void setUp() {
        taskMapper = mock(VideoTranscodeTaskMapper.class);
        resultService = mock(VideoTranscodeResultService.class);
        worker = mock(VideoTranscodeWorker.class);
        VideoTranscodeLeaseService leaseService = mock(VideoTranscodeLeaseService.class);
        lease = mock(VideoTranscodeLeaseService.Lease.class);

        task = new VideoTranscodeTask();
        task.setTaskId("task-1");
        task.setFileMd5("md5-1");
        task.setStatus(1);
        when(taskMapper.selectByTaskId("task-1")).thenReturn(task);
        when(taskMapper.markProcessing("task-1", 2, "owner-2")).thenReturn(1);
        when(leaseService.start("task-1", 2, "owner-2")).thenReturn(lease);

        consumer = new VideoTranscodeConsumer(taskMapper, worker, resultService, leaseService, 3, 10);
    }

    @Test
    void shouldPublishLowRenditionBeforeFinalAdaptiveResult() {
        MediaTranscodeResult playable = result("360P");
        MediaTranscodeResult adaptive = result("360P", "720P", "1080P");
        when(worker.transcode(eq(task), any(), any())).thenAnswer(invocation -> {
            Consumer<MediaTranscodeResult> listener = invocation.getArgument(1);
            listener.accept(playable);
            return adaptive;
        });

        consumer.onMessage(message());

        verify(resultService).publish(task, playable, false);
        verify(resultService).publish(task, adaptive, true);
    }

    @Test
    void shouldKeepPlayableResultWhenHigherRenditionsFail() {
        MediaTranscodeResult playable = result("360P");
        when(worker.transcode(eq(task), any(), any())).thenAnswer(invocation -> {
            Consumer<MediaTranscodeResult> listener = invocation.getArgument(1);
            listener.accept(playable);
            throw new IllegalStateException("1080P encoder unavailable");
        });

        consumer.onMessage(message());

        verify(taskMapper).markDegradedSuccess(eq("task-1"), eq(2L), eq("owner-2"), anyString(), eq(3), eq(10));
        verify(taskMapper, never()).markPendingAfterFailure(anyString(), anyLong(), anyString(), anyInt(),
                anyString(), anyInt(), anyInt());
    }

    @Test
    void shouldRetryWhenPublishingPlayableResultFails() {
        MediaTranscodeResult playable = result("360P");
        when(worker.transcode(eq(task), any(), any())).thenAnswer(invocation -> {
            Consumer<MediaTranscodeResult> listener = invocation.getArgument(1);
            listener.accept(playable);
            return playable;
        });
        doThrow(new IllegalStateException("video update failed"))
                .when(resultService).publish(task, playable, false);

        consumer.onMessage(message());

        verify(taskMapper).markPendingAfterFailure(eq("task-1"), eq(2L), eq("owner-2"), eq(2),
                anyString(), eq(3), eq(10));
    }

    @Test
    void shouldKeepPlayableResultWhenFinalDatabaseWriteFails() {
        MediaTranscodeResult playable = result("360P");
        MediaTranscodeResult adaptive = result("360P", "720P");
        when(worker.transcode(eq(task), any(), any())).thenAnswer(invocation -> {
            Consumer<MediaTranscodeResult> listener = invocation.getArgument(1);
            listener.accept(playable);
            return adaptive;
        });
        doThrow(new IllegalStateException("adaptive video update failed"))
                .when(resultService).publish(task, adaptive, true);

        consumer.onMessage(message());

        verify(resultService).publish(task, playable, false);
        verify(taskMapper).markDegradedSuccess(eq("task-1"), eq(2L), eq("owner-2"), anyString(), eq(3), eq(10));
    }

    @Test
    void compensationKeepsOldPlaybackUntilCompleteResultCommits() {
        task.setStatus(3);
        task.setRenditionStatus(5);
        MediaTranscodeResult playable = result("360P");
        MediaTranscodeResult adaptive = result("360P", "720P");
        when(worker.transcode(eq(task), any(), any())).thenAnswer(invocation -> {
            Consumer<MediaTranscodeResult> listener = invocation.getArgument(1);
            listener.accept(playable);
            return adaptive;
        });

        consumer.onMessage(message());

        verify(resultService, never()).publish(task, playable, false);
        verify(resultService).publish(task, adaptive, true);
    }

    @Test
    void compensationFailureKeepsPlayableTaskAndSchedulesRetry() {
        task.setStatus(3);
        task.setRenditionStatus(5);
        when(worker.transcode(eq(task), any(), any())).thenThrow(new IllegalStateException("high batch timeout"));

        consumer.onMessage(message());

        verify(taskMapper).markDegradedSuccess(eq("task-1"), eq(2L), eq("owner-2"),
                anyString(), eq(3), eq(10));
        verify(taskMapper, never()).markPendingAfterFailure(anyString(), anyLong(), anyString(),
                anyInt(), anyString(), anyInt(), anyInt());
        verifyNoInteractions(resultService);
    }

    @Test
    void staleMessageCannotClaimOrStartWorker() {
        when(taskMapper.markProcessing("task-1", 2, "owner-2")).thenReturn(0);

        consumer.onMessage(message());

        verifyNoInteractions(worker, resultService);
    }

    @Test
    void expiredWorkerCannotPublishOrResetNewOwner() {
        when(worker.transcode(eq(task), any(), any())).thenAnswer(invocation -> {
            Runnable guard = invocation.getArgument(2);
            guard.run();
            return result("360P");
        });
        doThrow(new VideoTranscodeLeaseService.LeaseLostException("task-1")).when(lease).assertHeld();

        consumer.onMessage(message());

        verifyNoInteractions(resultService);
        verify(taskMapper, never()).markPendingAfterFailure(anyString(), anyLong(), anyString(), anyInt(),
                anyString(), anyInt(), anyInt());
        verify(taskMapper, never()).markDegradedSuccess(anyString(), anyLong(), anyString(),
                anyString(), anyInt(), anyInt());
    }

    private VideoTranscodeMessage message() {
        VideoTranscodeMessage message = new VideoTranscodeMessage();
        message.setTaskId("task-1");
        message.setClaimGeneration(2L);
        message.setClaimToken("owner-2");
        return message;
    }

    private MediaTranscodeResult result(String... qualities) {
        List<MediaTranscodeResult.Variant> variants = java.util.Arrays.stream(qualities)
                .map(quality -> new MediaTranscodeResult.Variant(
                        quality, 640, 360, 800_000, "http://media/" + quality + "/index.m3u8"))
                .toList();
        return new MediaTranscodeResult("http://media/master.m3u8", "http://media/cover.jpg", variants);
    }
}
