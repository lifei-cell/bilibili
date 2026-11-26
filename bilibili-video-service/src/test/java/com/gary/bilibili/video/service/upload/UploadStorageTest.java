package com.gary.bilibili.video.service.upload;

import com.gary.bilibili.common.storage.ObjectStorage;
import com.gary.bilibili.video.entity.FileChunk;
import com.gary.bilibili.video.model.UploadTask;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class UploadStorageTest {
    private final ObjectStorage objects = mock(ObjectStorage.class);
    private final UploadStorage storage = new UploadStorage(objects, "http://storage/", "videos", "tmp");

    @Test
    void composesInOrderAndSkipsAnExistingTarget() throws Exception {
        FileChunk first = new FileChunk(); first.setObjectName("up/0");
        FileChunk second = new FileChunk(); second.setObjectName("up/1");
        storage.composeChunks(List.of(first, second), "source/x.mp4");
        verify(objects).compose("tmp", List.of("up/0", "up/1"), "videos", "source/x.mp4");
        when(objects.exists("videos", "source/x.mp4")).thenReturn(true);
        storage.composeChunks(List.of(first, second), "source/x.mp4");
        verify(objects, times(1)).compose(anyString(), anyList(), anyString(), anyString());
        assertThat(storage.sourceUrl("source/x.mp4")).isEqualTo("http://storage/videos/source/x.mp4");
    }

    @Test
    void doesNotTreatStorageOutageAsAMissingObject() throws Exception {
        when(objects.exists("videos", "target")).thenThrow(new IOException("offline"));
        assertThatThrownBy(() -> storage.composeChunks(List.of(), "target")).hasMessage("分片合并失败");
        verify(objects, never()).compose(anyString(), anyList(), anyString(), anyString());
    }

    @Test
    void preservesSafeObjectNaming() {
        UploadTask task = new UploadTask(); task.setFileMd5("hash"); task.setFileName("movie.MP4");
        assertThat(storage.buildSourceObjectName(task)).isEqualTo("source/hash.mp4");
        task.setFileName("movie.bad/path");
        assertThat(storage.buildSourceObjectName(task)).isEqualTo("source/hash");
    }
}
