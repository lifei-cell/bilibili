package com.gary.bilibili.video.service;

import com.gary.bilibili.video.entity.VideoTranscodeTask;
import io.minio.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Local real FFmpeg/ffprobe plus real S3 media; fault controls exist only in test files. */
final class TranscodeFaultMedia implements AutoCloseable {
    final String bucket = "matrix-" + UUID.randomUUID();
    final String endpoint;
    final MinioClient minio;
    final TranscodeFaultProxy proxy;
    final Path root;
    final Path work;
    final Path trigger;
    final Path marker;
    final Path wrapper;

    TranscodeFaultMedia(String endpoint, Path root) throws Exception {
        this.endpoint = endpoint;
        this.root = root;
        this.work = Files.createDirectory(root.resolve("work"));
        this.trigger = root.resolve("enabled");
        this.marker = root.resolve("fault-observed");
        this.wrapper = root.resolve("ffmpeg-fault.sh");
        minio = client(endpoint);
        minio.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        minio.setBucketPolicy(SetBucketPolicyArgs.builder().bucket(bucket).config("""
                {"Version":"2012-10-17","Statement":[{"Effect":"Allow","Principal":{"AWS":["*"]},
                "Action":["s3:GetObject"],"Resource":["arn:aws:s3:::%s/*"]}]}
                """.formatted(bucket)).build());
        Path source = root.resolve("source.mp4");
        command("ffmpeg", "-v", "error", "-y", "-f", "lavfi", "-i", "testsrc2=size=1280x720:rate=12",
                "-t", "2", "-c:v", "libx264", "-preset", "ultrafast", "-pix_fmt", "yuv420p", source.toString());
        minio.uploadObject(UploadObjectArgs.builder().bucket(bucket).object("source.mp4")
                .filename(source.toString()).build());
        proxy = new TranscodeFaultProxy(endpoint);
    }

    private MinioClient client(String address) {
        return MinioClient.builder().endpoint(address).credentials("minioadmin", "minioadmin").build();
    }

    FfmpegVideoTranscodeWorker worker(String fault, boolean afterPlayable) throws Exception {
        String quality = afterPlayable ? "720p" : "360p";
        Files.writeString(trigger, "enabled");
        if (fault.equals("MINIO_WRITE")) proxy.failMasterContaining(quality);
        // Only HLS batch commands match. Probing and cover extraction still run normally.
        Files.writeString(wrapper, """
                #!/bin/sh
                if [ -f '%s' ]; then
                  case "$*" in
                    *'name:%s'*)
                      if [ '%s' = 'FFMPEG_EXIT' ]; then
                        echo injected-ffmpeg-exit > '%s'
                        echo injected-ffmpeg-exit >&2
                        exec ffmpeg -v error -f lavfi -i color -frames:v 1 -c:v missing_fault_encoder -f null -
                      fi
                      if [ '%s' = 'FFMPEG_HANG' ]; then
                        ffmpeg "$@" &
                        child=$!
                        kill -STOP "$child"
                        echo "$$ $child" > '%s'
                        wait "$child"
                        exit $?
                      fi
                      ;;
                  esac
                fi
                exec ffmpeg "$@"
                """.formatted(trigger, quality, fault, marker, fault, marker));
        Files.setPosixFilePermissions(wrapper, PosixFilePermissions.fromString("rwx------"));
        return new FfmpegVideoTranscodeWorker(new HlsPackageStorage(client(proxy.endpoint()), bucket, endpoint, "play"),
                new VideoEncoderSelector(wrapper.toString(), "software"), wrapper.toString(), "ffprobe",
                work.toString(), 8, "ultrafast", 28, 2, false, true);
    }

    void repair() throws Exception {
        Files.deleteIfExists(trigger);
        proxy.failMasterContaining(null);
    }

    void resetEvidence() throws Exception {
        Files.deleteIfExists(marker);
        proxy.acceptedMediaWrites.set(0);
        proxy.rejectedWrites.set(0);
    }

    void assertInjected(String fault) throws Exception {
        if (fault.equals("MINIO_WRITE")) {
            assertThat(proxy.acceptedMediaWrites.get()).as("real partial media PUTs before failure").isPositive();
            assertThat(proxy.rejectedWrites.get()).as("real HTTP 503 at master publication").isPositive();
        } else {
            assertThat(marker).exists();
            if (fault.equals("FFMPEG_HANG")) {
                for (String pid : Files.readString(marker).trim().split(" ")) {
                    assertThat(ProcessHandle.of(Long.parseLong(pid)).map(ProcessHandle::isAlive).orElse(false))
                            .as("timed-out wrapper/encoder must be reaped: %s", pid).isFalse();
                }
            }
        }
        try (var paths = Files.list(work)) {
            assertThat(paths.toList()).as("temporary transcode files reclaimed").isEmpty();
        }
    }

    void assertPlayable(String masterUrl) throws Exception {
        assertThat(masterUrl).isNotBlank();
        // Decode actual media, not just a 200 response containing #EXTM3U.
        command("ffmpeg", "-v", "error", "-xerror", "-i", masterUrl, "-f", "null", "-");
    }

    List<String> objects(VideoTranscodeTask task) throws Exception {
        List<String> objects = new ArrayList<>();
        for (var result : minio.listObjects(ListObjectsArgs.builder().bucket(bucket)
                .prefix("play/" + task.getFileMd5() + "/").recursive(true).build())) {
            objects.add(result.get().objectName());
        }
        return objects;
    }

    private void command(String... args) throws Exception {
        Path log = root.resolve("command.log");
        Process process = new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        int exit = MediaProcessRunner.await(process, Duration.ofMinutes(1).toSeconds(), "Fixture media command timeout");
        assertThat(exit).as(Files.readString(log)).isZero();
    }

    @Override
    public void close() { proxy.close(); }
}
