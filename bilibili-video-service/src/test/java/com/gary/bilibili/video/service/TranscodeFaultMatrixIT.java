package com.gary.bilibili.video.service;

import com.gary.bilibili.video.consumer.VideoTranscodeConsumer;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.model.MediaTranscodeResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Opt-in real media matrix; the dedicated Linux CI job supplies FFmpeg and Docker. */
@Testcontainers
@EnabledIfSystemProperty(named = "transcode.fault.matrix", matches = "true")
@Timeout(180)
class TranscodeFaultMatrixIT {
    @Container
    static final MySQLContainer MYSQL = new MySQLContainer(DockerImageName.parse("mysql:8.4"))
            .withDatabaseName("bilibili").withUsername("bilibili").withPassword("bilibili_test");
    @Container
    static final GenericContainer<?> MINIO = new GenericContainer<>(
            DockerImageName.parse(System.getenv().getOrDefault("MINIO_TEST_IMAGE",
                    "quay.io/minio/minio:RELEASE.2024-12-13T22-19-12Z")))
            .withEnv("MINIO_ROOT_USER", "minioadmin").withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withCommand("server", "/data").withExposedPorts(9000)
            .waitingFor(Wait.forHttp("/minio/health/ready").forPort(9000));
    @TempDir
    Path temporary;

    @ParameterizedTest(name = "{0}, afterPlayable={1}")
    @CsvSource({"FFMPEG_EXIT,false", "FFMPEG_HANG,false", "MINIO_WRITE,false",
            "FFMPEG_EXIT,true", "FFMPEG_HANG,true", "MINIO_WRITE,true"})
    void failureConvergesAndRepairPreservesPlayback(String fault, boolean afterPlayable) throws Exception {
        try (var media = new TranscodeFaultMedia(endpoint(), temporary);
             var db = database(media)) {
            var worker = media.worker(fault, afterPlayable);
            var consumer = consumer(db, worker);
            VideoTranscodeTask stale = null;
            String lowUrl = null;
            // Initial failure plus two bounded adaptive retries, or two pre-playback attempts.
            int attempts = afterPlayable ? 3 : 2;
            for (int attempt = 0; attempt < attempts; attempt++) {
                media.resetEvidence();
                var message = db.claim();
                if (stale == null) stale = db.row();
                consumer.onMessage(message);
                media.assertInjected(fault);
                var row = db.row();
                assertThat(row.getClaimToken()).isNull();
                assertThat(row.getLeaseUntil()).isNull();
                String error = afterPlayable ? row.getRenditionErrorMessage() : row.getErrorMessage();
                assertThat(error).contains(switch (fault) {
                    case "FFMPEG_EXIT" -> "missing_fault_encoder";
                    case "FFMPEG_HANG" -> "超时";
                    default -> "视频转码失败";
                });
                if (afterPlayable) {
                    if (lowUrl == null) lowUrl = row.getOutputUrl();
                    db.assertPlaybackReference(lowUrl);
                    media.assertPlayable(lowUrl);
                    assertThat(row.getStatus()).isEqualTo(3);
                    assertThat(row.getRenditionStatus()).isEqualTo(attempt == attempts - 1 ? 4 : 3);
                    assertThat(row.getRenditionRetryCount()).isEqualTo(attempt);
                } else {
                    db.assertPlaybackReference(null);
                    assertThat(row.getStatus()).isEqualTo(attempt == attempts - 1 ? 4 : 0);
                    assertThat(row.getRetryCount()).isEqualTo(attempt + 1);
                }
            }
            assertThat(db.tasks.selectDispatchable(100)).extracting(VideoTranscodeTask::getTaskId)
                    .doesNotContain(db.taskId);
            assertThat(db.tasks.markDispatched(db.taskId, db.row().getClaimGeneration(),
                    java.util.UUID.randomUUID().toString(), 120, 2)).isZero();

            var beforeCleanup = media.objects(db.row());
            if (fault.equals("MINIO_WRITE")) {
                assertThat(beforeCleanup).as("failed PUT left real partial packages for cleanup").isNotEmpty();
            }
            if (afterPlayable) {
                assertThat(beforeCleanup.stream().filter(name -> !lowUrlContainsAttempt(db.row().getOutputUrl(), name)))
                        .as("failed compensation attempts left unreferenced objects").isNotEmpty();
            }
            var cleanup = new HlsAttemptCleanupTask(db.tasks, media.minio, media.bucket, "play", 168);
            cleanup.cleanupTask(db.row(), Instant.now().minusSeconds(3600));
            assertThat(media.objects(db.row())).containsExactlyInAnyOrderElementsOf(beforeCleanup);
            // Simulate retention expiry at the cleanup boundary; never lower production retention.
            cleanup.cleanupTask(db.row(), Instant.now().plusSeconds(60));
            if (afterPlayable) {
                media.assertPlayable(lowUrl);
                assertThat(media.objects(db.row())).allMatch(name -> lowUrlContainsAttempt(db.row().getOutputUrl(), name));
            } else {
                assertThat(media.objects(db.row())).isEmpty();
            }

            media.repair();
            if (afterPlayable) {
                assertThat(db.tasks.requeueFailedRenditions(db.taskId)).isEqualTo(1);
            } else {
                assertThat(db.tasks.resetPending(db.taskId, endpoint() + "/" + media.bucket + "/source.mp4",
                        "source.mp4", "matrix.mp4", 1L)).isEqualTo(1);
            }
            consumer.onMessage(db.claim());
            var recovered = db.row();
            assertThat(recovered.getStatus()).isEqualTo(3);
            assertThat(recovered.getRenditionStatus()).isEqualTo(2);
            assertThat(recovered.getClaimToken()).isNull();
            assertThat(recovered.getVariantsJson()).contains("360P", "720P");
            db.assertPlaybackReference(recovered.getOutputUrl());
            media.assertPlayable(recovered.getOutputUrl());
            assertStaleRejected(db, stale, recovered.getOutputUrl());

            assertFinalCleanup(db, media, cleanup);
        }
    }

    @Test
    void lateWorkerCannotPublishAfterLiveTakeover() throws Exception {
        try (var media = new TranscodeFaultMedia(endpoint(), temporary);
             var db = database(media);
             var executor = Executors.newSingleThreadExecutor()) {
            var real = media.worker("NONE", true);
            CountDownLatch playable = new CountDownLatch(1);
            CountDownLatch releaseOld = new CountDownLatch(1);
            AtomicReference<String> lowUrl = new AtomicReference<>();
            VideoTranscodeWorker paused = (task, listener, lease) -> real.transcode(task, result -> {
                listener.accept(result);
                lowUrl.set(result.masterUrl());
                playable.countDown();
                try {
                    if (!releaseOld.await(60, TimeUnit.SECONDS)) throw new IllegalStateException("Takeover timed out");
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }, lease);
            var oldMessage = db.claim();
            var stale = db.row();
            var old = executor.submit(() -> consumer(db, paused).onMessage(oldMessage));
            try {
                assertThat(playable.await(30, TimeUnit.SECONDS)).isTrue();
                media.assertPlayable(lowUrl.get());
                var cleanup = new HlsAttemptCleanupTask(db.tasks, media.minio, media.bucket, "play", 168);
                // Active and referenced packages survive even when retention has elapsed.
                cleanup.cleanupTask(db.row(), Instant.now().plusSeconds(60));
                media.assertPlayable(lowUrl.get());
                db.jdbc.update("update video_transcode_task set lease_until=timestampadd(SECOND,-1,now(6)) where task_id=?", db.taskId);
                var replacement = db.claim();
                db.assertPlaybackReference(lowUrl.get());
                assertStaleRejected(db, stale, lowUrl.get());
                consumer(db, real).onMessage(replacement);
                String winner = db.row().getOutputUrl();
                assertThat(winner).isNotEqualTo(lowUrl.get());
                releaseOld.countDown();
                old.get(30, TimeUnit.SECONDS);
                db.assertPlaybackReference(winner);
                assertStaleRejected(db, stale, winner);
                assertFinalCleanup(db, media, cleanup);
            } finally {
                releaseOld.countDown();
                old.get(30, TimeUnit.SECONDS);
            }
        }
    }

    private static void assertFinalCleanup(TranscodeFaultDatabase db, TranscodeFaultMedia media,
                                          HlsAttemptCleanupTask cleanup) throws Exception {
        var current = db.row();
        String cover = db.jdbc.queryForObject("select cover_url from video where file_md5=?", String.class, db.fileMd5);
        cleanup.cleanupTask(current, Instant.now().plusSeconds(60));
        // VideoMapper preserves a video's existing cover. Its old attempt is still referenced.
        assertThat(media.objects(current)).isNotEmpty().allMatch(name ->
                lowUrlContainsAttempt(current.getOutputUrl(), name) || lowUrlContainsAttempt(cover, name));
        media.assertPlayable(cover);
        media.assertPlayable(current.getOutputUrl());
        // Simulate selecting the new cover, releasing the final reference to the old package.
        db.jdbc.update("update video set cover_url=? where file_md5=?", current.getCoverUrl(), db.fileMd5);
        cleanup.cleanupTask(current, Instant.now().plusSeconds(60));
        assertThat(media.objects(current)).isNotEmpty().allMatch(name ->
                lowUrlContainsAttempt(current.getOutputUrl(), name));
        media.assertPlayable(current.getOutputUrl());
    }

    private static void assertStaleRejected(TranscodeFaultDatabase db, VideoTranscodeTask stale, String winner) {
        var late = new MediaTranscodeResult("http://stale/master.m3u8", "http://stale/cover.jpg", List.of());
        assertThatThrownBy(() -> db.results.publish(stale, late, true))
                .isInstanceOf(VideoTranscodeLeaseService.LeaseLostException.class);
        assertThat(db.tasks.markPendingAfterFailure(db.taskId, stale.getClaimGeneration(), stale.getClaimToken(),
                2, "late failure", 2, 1)).isZero();
        assertThat(db.tasks.markDegradedSuccess(db.taskId, stale.getClaimGeneration(), stale.getClaimToken(),
                "late adaptive failure", 2, 1)).isZero();
        db.assertPlaybackReference(winner);
    }

    private static boolean lowUrlContainsAttempt(String url, String object) {
        return url.contains(object.substring(0, object.indexOf('/', object.indexOf("attempt-"))) + "/");
    }

    private static VideoTranscodeConsumer consumer(TranscodeFaultDatabase db, VideoTranscodeWorker worker) {
        return new VideoTranscodeConsumer(db.tasks, worker, db.results,
                new VideoTranscodeLeaseService(db.tasks, 120), 2, 1);
    }

    private static TranscodeFaultDatabase database(TranscodeFaultMedia media) throws Exception {
        return new TranscodeFaultDatabase(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword(),
                endpoint() + "/" + media.bucket + "/source.mp4");
    }

    private static String endpoint() { return "http://" + MINIO.getHost() + ":" + MINIO.getMappedPort(9000); }
}
