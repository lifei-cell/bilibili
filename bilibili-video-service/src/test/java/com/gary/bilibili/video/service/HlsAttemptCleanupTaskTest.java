package com.gary.bilibili.video.service;

import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class HlsAttemptCleanupTaskTest {

    private static final String OLD = "play/abc/attempt-1-11111111-1111-1111-1111-111111111111/";
    private static final String LIVE = "play/abc/attempt-2-22222222-2222-2222-2222-222222222222/";
    private static final String RECENT = "play/abc/attempt-3-33333333-3333-3333-3333-333333333333/";

    @SuppressWarnings("unchecked")
    @Test
    void removesOnlyAgedUnreferencedAndUnleasedAttempt() throws Exception {
        VideoTranscodeTaskMapper mapper = mock(VideoTranscodeTaskMapper.class);
        MinioClient minio = mock(MinioClient.class);
        VideoTranscodeTask task = new VideoTranscodeTask();
        task.setId(1L);
        task.setTaskId("task-1");
        task.setFileMd5("abc");
        when(mapper.selectCleanupCandidates(0, 168, 10)).thenReturn(List.of(task));
        Result<Item> old = result(OLD + "master.m3u8", ZonedDateTime.now().minusDays(9));
        Result<Item> live = result(LIVE + "master.m3u8", ZonedDateTime.now().minusDays(9));
        Result<Item> recent = result(RECENT + "master.m3u8", ZonedDateTime.now().minusDays(1));
        when(minio.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(old, live, recent));
        when(mapper.countAttemptReferences(eq("/videos/" + LIVE))).thenReturn(1L);

        new HlsAttemptCleanupTask(mapper, minio, "videos", "play", 168).cleanup();

        verify(minio, times(1)).removeObject(any(RemoveObjectArgs.class));
        verify(mapper, never()).countAttemptReferences("/videos/" + RECENT);
    }

    @SuppressWarnings("unchecked")
    @Test
    void activeOwnerPreventsDeletionEvenWhenObjectsAreOld() throws Exception {
        VideoTranscodeTaskMapper mapper = mock(VideoTranscodeTaskMapper.class);
        MinioClient minio = mock(MinioClient.class);
        VideoTranscodeTask task = new VideoTranscodeTask();
        task.setId(1L);
        task.setTaskId("task-1");
        task.setFileMd5("abc");
        when(mapper.selectCleanupCandidates(0, 168, 10)).thenReturn(List.of(task));
        Result<Item> old = result(OLD + "master.m3u8", ZonedDateTime.now().minusDays(9));
        when(minio.listObjects(any(ListObjectsArgs.class))).thenReturn(List.of(old));
        when(mapper.countActiveAttempt("abc", 1,
                "11111111-1111-1111-1111-111111111111")).thenReturn(1L);

        new HlsAttemptCleanupTask(mapper, minio, "videos", "play", 168).cleanup();

        verify(minio, never()).removeObject(any(RemoveObjectArgs.class));
    }

    @SuppressWarnings("unchecked")
    private Result<Item> result(String name, ZonedDateTime timestamp) throws Exception {
        Result<Item> result = mock(Result.class);
        Item item = mock(Item.class);
        when(result.get()).thenReturn(item);
        when(item.objectName()).thenReturn(name);
        when(item.lastModified()).thenReturn(timestamp);
        return result;
    }
}
