package com.gary.bilibili.video.service;

import com.gary.bilibili.video.model.MediaTranscodeResult;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class HlsPackageStorageTest {

    @TempDir
    Path tempDirectory;

    @Test
    void shouldPublishMasterPlaylistAfterSegments() throws Exception {
        MinioClient minioClient = mock(MinioClient.class);
        List<String> uploadedObjects = new ArrayList<>();
        doAnswer(invocation -> {
            uploadedObjects.add(invocation.<PutObjectArgs>getArgument(0).object());
            return null;
        }).when(minioClient).putObject(any(PutObjectArgs.class));

        Path rendition = tempDirectory.resolve("360p");
        Files.createDirectories(rendition);
        Files.writeString(rendition.resolve("seg_00000.ts"), "segment");
        Files.writeString(rendition.resolve("index.m3u8"), "playlist");
        Files.writeString(tempDirectory.resolve("cover.jpg"), "cover");

        HlsPackageStorage storage = new HlsPackageStorage(
                minioClient, "videos", "http://media.example.com", "play");
        MediaTranscodeResult.Variant variant = new MediaTranscodeResult.Variant(
                "360P", 640, 360, 800_000, "http://media.example.com/videos/play/id/360p/index.m3u8");

        storage.publish(tempDirectory, "play/id", List.of(variant));

        assertEquals("play/id/master.m3u8", uploadedObjects.getLast());
    }
}
