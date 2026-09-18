package com.gary.bilibili.video.service;

import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import io.minio.ListObjectsArgs;
import io.minio.MinioClient;
import io.minio.RemoveObjectArgs;
import io.minio.Result;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Deletes aged, unreferenced HLS attempts after checking the current database owner. */
@Component
@ConditionalOnProperty(name = {"video.transcode.publisher-enabled", "video.transcode.cleanup-enabled"},
        havingValue = "true", matchIfMissing = true)
public class HlsAttemptCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(HlsAttemptCleanupTask.class);
    private static final int TASK_BATCH_SIZE = 10;
    private static final int MAX_OBJECTS_PER_TASK = 10_000;
    private static final Pattern ATTEMPT = Pattern.compile("attempt-([1-9][0-9]*)-"
            + "([0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12})");

    private final VideoTranscodeTaskMapper taskMapper;
    private final MinioClient minioClient;
    private final String bucket;
    private final String outputPrefix;
    private final int retentionHours;
    private long cursor;

    public HlsAttemptCleanupTask(VideoTranscodeTaskMapper taskMapper, MinioClient minioClient,
                                 @Value("${minio.video-bucket}") String bucket,
                                 @Value("${video.transcode.output-prefix:play}") String outputPrefix,
                                 @Value("${video.transcode.cleanup-retention-hours:168}") int retentionHours) {
        this.taskMapper = taskMapper;
        this.minioClient = minioClient;
        this.bucket = bucket;
        String normalized = outputPrefix.replaceAll("^/+|/+$", "");
        this.outputPrefix = normalized.isBlank() ? "play" : normalized;
        this.retentionHours = Math.max(1, retentionHours);
    }

    @Scheduled(fixedDelayString = "${video.transcode.cleanup-interval:300000}",
            initialDelayString = "${video.transcode.cleanup-interval:300000}")
    public void cleanup() {
        List<VideoTranscodeTask> tasks = taskMapper.selectCleanupCandidates(
                cursor, retentionHours, TASK_BATCH_SIZE);
        if (tasks.isEmpty()) {
            cursor = 0;
            return;
        }
        Instant cutoff = Instant.now().minus(retentionHours, ChronoUnit.HOURS);
        for (VideoTranscodeTask task : tasks) {
            cursor = task.getId();
            try {
                cleanupTask(task, cutoff);
            } catch (Exception exception) {
                // The next scan cycle retries the same task; cleanup must never block dispatch.
                log.warn("HLS attempt cleanup failed, taskId={}", task.getTaskId(), exception);
            }
        }
    }

    void cleanupTask(VideoTranscodeTask task, Instant cutoff) throws Exception {
        String key = StringUtils.hasText(task.getFileMd5()) ? task.getFileMd5() : task.getTaskId();
        if (!StringUtils.hasText(key) || key.contains("/") || key.contains("..")) return;
        String root = outputPrefix + "/" + key + "/";
        Map<String, Attempt> attempts = new HashMap<>();
        int seen = 0;
        for (Result<Item> result : minioClient.listObjects(ListObjectsArgs.builder()
                .bucket(bucket).prefix(root).recursive(true).build())) {
            Item item = result.get();
            if (++seen > MAX_OBJECTS_PER_TASK) {
                log.warn("HLS attempt cleanup skipped oversized key, taskId={}", task.getTaskId());
                return;
            }
            String relative = item.objectName().substring(root.length());
            int slash = relative.indexOf('/');
            if (slash < 0) continue;
            Matcher match = ATTEMPT.matcher(relative.substring(0, slash));
            if (!match.matches()) continue;
            String prefix = root + match.group() + "/";
            Attempt attempt = attempts.computeIfAbsent(prefix,
                    ignored -> new Attempt(Long.parseLong(match.group(1)), match.group(2)));
            attempt.objects.add(item.objectName());
            if (item.lastModified() == null || item.lastModified().toInstant().isAfter(cutoff)) {
                attempt.recent = true;
            }
        }
        for (Map.Entry<String, Attempt> entry : attempts.entrySet()) {
            Attempt attempt = entry.getValue();
            String prefix = entry.getKey();
            if (attempt.recent || taskMapper.countActiveAttempt(task.getFileMd5(),
                    attempt.generation, attempt.token) > 0
                    || taskMapper.countAttemptReferences("/" + bucket + "/" + prefix) > 0) {
                continue;
            }
            for (String object : attempt.objects) {
                minioClient.removeObject(RemoveObjectArgs.builder().bucket(bucket).object(object).build());
            }
            log.info("Removed unreferenced HLS attempt, taskId={}, prefix={}, objects={}",
                    task.getTaskId(), prefix, attempt.objects.size());
        }
    }

    private static final class Attempt {
        private final long generation;
        private final String token;
        private final List<String> objects = new ArrayList<>();
        private boolean recent;

        private Attempt(long generation, String token) {
            this.generation = generation;
            this.token = token;
        }
    }
}
