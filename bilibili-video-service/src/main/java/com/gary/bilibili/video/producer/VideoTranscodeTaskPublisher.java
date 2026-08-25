package com.gary.bilibili.video.producer;

import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.message.VideoTranscodeMessage;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Component
public class VideoTranscodeTaskPublisher {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeTaskPublisher.class);
    private static final int BATCH_SIZE = 20;
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final VideoTranscodeTaskMapper taskMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final StringRedisTemplate stringRedisTemplate;
    private final int maxRetries;
    private final int retryDelaySeconds;
    private final int reclaimAfterSeconds;

    public VideoTranscodeTaskPublisher(VideoTranscodeTaskMapper taskMapper,
                                       RocketMQTemplate rocketMQTemplate,
                                       StringRedisTemplate stringRedisTemplate,
                                       @Value("${video.transcode.max-retries:3}") int maxRetries,
                                       @Value("${video.transcode.retry-delay-seconds:10}") int retryDelaySeconds,
                                       @Value("${video.transcode.reclaim-after-seconds:1860}") int reclaimAfterSeconds) {
        this.taskMapper = taskMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
        this.maxRetries = Math.max(1, maxRetries);
        this.retryDelaySeconds = Math.max(1, retryDelaySeconds);
        this.reclaimAfterSeconds = Math.max(60, reclaimAfterSeconds);
    }

    @Scheduled(
            fixedDelayString = "${video.transcode.dispatch-interval:1000}",
            initialDelayString = "${video.transcode.dispatch-initial-delay:5000}")
    public void dispatchPendingTasks() {
        List<VideoTranscodeTask> tasks = taskMapper.selectDispatchable(BATCH_SIZE, reclaimAfterSeconds);
        for (VideoTranscodeTask task : tasks) {
            dispatch(task);
        }
    }

    private void dispatch(VideoTranscodeTask task) {
        String lockKey = UploadConstant.TRANSCODE_DISPATCH_LOCK_KEY_PREFIX + task.getTaskId();
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(30));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        try {
            if (taskMapper.markDispatched(task.getTaskId(), reclaimAfterSeconds) == 0) {
                return;
            }
            VideoTranscodeMessage message = new VideoTranscodeMessage();
            message.setTaskId(task.getTaskId());
            message.setUserId(task.getUserId());
            message.setFileMd5(task.getFileMd5());
            message.setFileName(task.getFileName());
            message.setFileSize(task.getFileSize());
            message.setSourceUrl(task.getSourceUrl());
            message.setSourceObjectName(task.getSourceObjectName());
            message.setCreateTime(java.time.LocalDateTime.now());
            rocketMQTemplate.convertAndSend(UploadConstant.TRANSCODE_TOPIC, message);
        } catch (Exception exception) {
            taskMapper.markPendingAfterFailure(task.getTaskId(), safeMessage(exception),
                    maxRetries, retryDelaySeconds);
            log.error("Dispatch video transcode task failed, taskId={}", task.getTaskId(), exception);
        } finally {
            try {
                stringRedisTemplate.execute(
                        RELEASE_LOCK_SCRIPT,
                        Collections.singletonList(lockKey),
                        lockValue);
            } catch (Exception exception) {
                log.warn("Release video transcode dispatch lock failed, taskId={}",
                        task.getTaskId(), exception);
            }
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
