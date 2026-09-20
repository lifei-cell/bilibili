package com.gary.bilibili.video.service;

import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import io.minio.ListObjectsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Testcontainers
class HlsAttemptCleanupIT {

    private static final String OLD = "play/abc/attempt-1-11111111-1111-1111-1111-111111111111/master.m3u8";
    private static final String REFERENCED =
            "play/abc/attempt-2-22222222-2222-2222-2222-222222222222/master.m3u8";

    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse("quay.io/minio/minio:RELEASE.2024-12-13T22-19-12Z"))
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data")
            .withExposedPorts(9000);

    @Test
    void deletesOrphanButPreservesReferencedPackageInRealMinio() throws Exception {
        MinioClient minio = MinioClient.builder()
                .endpoint("http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000))
                .credentials("minioadmin", "minioadmin").build();
        minio.makeBucket(MakeBucketArgs.builder().bucket("videos").build());
        put(minio, OLD);
        put(minio, REFERENCED);

        VideoTranscodeTaskMapper mapper = mock(VideoTranscodeTaskMapper.class);
        when(mapper.countAttemptReferences("/videos/play/abc/attempt-2-"
                + "22222222-2222-2222-2222-222222222222/")).thenReturn(1L);
        VideoTranscodeTask task = new VideoTranscodeTask();
        task.setTaskId("task-1");
        task.setFileMd5("abc");

        // The scheduler supplies an aged cutoff; use a future cutoff to test deletion immediately.
        new HlsAttemptCleanupTask(mapper, minio, "videos", "play", 168)
                .cleanupTask(task, Instant.now().plusSeconds(60));

        List<String> remaining = new ArrayList<>();
        for (Result<Item> result : minio.listObjects(ListObjectsArgs.builder()
                .bucket("videos").prefix("play/abc/").recursive(true).build())) {
            remaining.add(result.get().objectName());
        }
        assertThat(remaining).containsExactly(REFERENCED);
    }

    private void put(MinioClient minio, String object) throws Exception {
        byte[] data = "#EXTM3U\n".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        minio.putObject(PutObjectArgs.builder().bucket("videos").object(object)
                .stream(new ByteArrayInputStream(data), data.length, -1)
                .contentType("application/vnd.apple.mpegurl").build());
    }
}
