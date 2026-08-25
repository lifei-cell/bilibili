package com.gary.bilibili.video.consumer;

import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.message.VideoTranscodeMessage;
import com.gary.bilibili.video.service.VideoTranscodeWorker;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

@Service
@RocketMQMessageListener(
        topic = UploadConstant.TRANSCODE_TOPIC,
        consumerGroup = "video-transcode-consumer",
        consumeThreadNumber = 4)
public class VideoTranscodeConsumer implements RocketMQListener<VideoTranscodeMessage> {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeConsumer.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final VideoTranscodeTaskMapper taskMapper;
    private final VideoMapper videoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final VideoTranscodeWorker transcodeWorker;
    private final int maxRetries;
    private final int retryDelaySeconds;
    private final int processingLockSeconds;

    public VideoTranscodeConsumer(VideoTranscodeTaskMapper taskMapper,
                                  VideoMapper videoMapper,
                                  StringRedisTemplate stringRedisTemplate,
                                  VideoTranscodeWorker transcodeWorker,
                                  @Value("${video.transcode.max-retries:3}") int maxRetries,
                                  @Value("${video.transcode.retry-delay-seconds:10}") int retryDelaySeconds,
                                  @Value("${video.transcode.processing-lock-seconds:1860}") int processingLockSeconds) {
        this.taskMapper = taskMapper;
        this.videoMapper = videoMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.transcodeWorker = transcodeWorker;
        this.maxRetries = Math.max(1, maxRetries);
        this.retryDelaySeconds = Math.max(1, retryDelaySeconds);
        this.processingLockSeconds = Math.max(60, processingLockSeconds);
    }

    @Override
    public void onMessage(VideoTranscodeMessage message) {
        if (message == null || !StringUtils.hasText(message.getTaskId())) {
            return;
        }

        VideoTranscodeTask task = taskMapper.selectByTaskId(message.getTaskId());
        if (task == null || Integer.valueOf(UploadConstant.TRANSCODE_STATUS_SUCCESS)
                .equals(task.getStatus())) {
            return;
        }

        String lockKey = UploadConstant.TRANSCODE_DISPATCH_LOCK_KEY_PREFIX + "processing:" + task.getTaskId();
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(processingLockSeconds));
        if (!Boolean.TRUE.equals(locked)) {
            return;
        }

        try {
            if (taskMapper.markProcessing(task.getTaskId()) == 0) {
                return;
            }
            String outputUrl = transcodeWorker.transcode(task);
            taskMapper.markSuccess(task.getTaskId(), outputUrl);
            videoMapper.updatePlayUrlByFileMd5(task.getFileMd5(), outputUrl);
        } catch (Exception exception) {
            taskMapper.markPendingAfterFailure(task.getTaskId(), safeMessage(exception),
                    maxRetries, retryDelaySeconds);
            log.error("Process video transcode task failed, taskId={}", task.getTaskId(), exception);
        } finally {
            try {
                stringRedisTemplate.execute(
                        RELEASE_LOCK_SCRIPT,
                        Collections.singletonList(lockKey),
                        lockValue);
            } catch (Exception exception) {
                log.warn("Release video transcode processing lock failed, taskId={}",
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
