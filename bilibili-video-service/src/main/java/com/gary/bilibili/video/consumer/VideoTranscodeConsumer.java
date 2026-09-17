package com.gary.bilibili.video.consumer;

import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.message.VideoTranscodeMessage;
import com.gary.bilibili.video.model.MediaTranscodeResult;
import com.gary.bilibili.video.service.VideoTranscodeLeaseService;
import com.gary.bilibili.video.service.VideoTranscodeWorker;
import com.gary.bilibili.video.service.VideoTranscodeResultService;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.util.concurrent.atomic.AtomicBoolean;

@Service
@ConditionalOnProperty(name = "video.transcode.consumer-enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = UploadConstant.TRANSCODE_TOPIC,
        consumerGroup = "video-transcode-consumer",
        // Keep one FFmpeg job per service instance; scale queue throughput with Worker replicas.
        consumeThreadNumber = 1)
public class VideoTranscodeConsumer implements RocketMQListener<VideoTranscodeMessage> {

    private static final Logger log = LoggerFactory.getLogger(VideoTranscodeConsumer.class);
    private final VideoTranscodeTaskMapper taskMapper;
    private final VideoTranscodeWorker transcodeWorker;
    private final VideoTranscodeResultService resultService;
    private final VideoTranscodeLeaseService leaseService;
    private final int maxRetries;
    private final int retryDelaySeconds;

    public VideoTranscodeConsumer(VideoTranscodeTaskMapper taskMapper,
                                  VideoTranscodeWorker transcodeWorker,
                                  VideoTranscodeResultService resultService,
                                  VideoTranscodeLeaseService leaseService,
                                  @Value("${video.transcode.max-retries:3}") int maxRetries,
                                  @Value("${video.transcode.retry-delay-seconds:10}") int retryDelaySeconds) {
        this.taskMapper = taskMapper;
        this.transcodeWorker = transcodeWorker;
        this.resultService = resultService;
        this.leaseService = leaseService;
        this.maxRetries = Math.max(1, maxRetries);
        this.retryDelaySeconds = Math.max(1, retryDelaySeconds);
    }

    @Override
    public void onMessage(VideoTranscodeMessage message) {
        if (message == null || !StringUtils.hasText(message.getTaskId())
                || message.getClaimGeneration() == null || !StringUtils.hasText(message.getClaimToken())) {
            return;
        }

        VideoTranscodeTask task = taskMapper.selectByTaskId(message.getTaskId());
        if (task == null || Integer.valueOf(UploadConstant.TRANSCODE_STATUS_SUCCESS)
                .equals(task.getStatus())) {
            return;
        }

        long generation = message.getClaimGeneration();
        String token = message.getClaimToken();
        if (taskMapper.markProcessing(task.getTaskId(), generation, token) != 1) {
            return;
        }
        log.info("Transcode task claimed, taskId={}, generation={}", task.getTaskId(), generation);
        task.setClaimGeneration(generation);
        task.setClaimToken(token);

        AtomicBoolean playablePublished = new AtomicBoolean(false);
        try (VideoTranscodeLeaseService.Lease lease = leaseService.start(task.getTaskId(), generation, token)) {
            MediaTranscodeResult result = transcodeWorker.transcode(task, playable -> {
                lease.assertHeld();
                resultService.publish(task, playable, false);
                playablePublished.set(true);
                log.info("Low rendition published, taskId={}, variants={}",
                        task.getTaskId(), playable.variants().size());
            }, lease::assertHeld);
            lease.assertHeld();
            resultService.publish(task, result, true);
        } catch (VideoTranscodeLeaseService.LeaseLostException exception) {
            log.info("Ignore result from expired transcode attempt, taskId={}, generation={}",
                    task.getTaskId(), generation);
        } catch (Exception exception) {
            if (playablePublished.get()) {
                taskMapper.markDegradedSuccess(task.getTaskId(), generation, token, safeMessage(exception));
                log.warn("Adaptive renditions failed after playable rendition was published, taskId={}",
                        task.getTaskId(), exception);
            } else {
                taskMapper.markPendingAfterFailure(task.getTaskId(), generation, token,
                        UploadConstant.TRANSCODE_STATUS_PROCESSING, safeMessage(exception), maxRetries, retryDelaySeconds);
                log.error("Process video transcode task failed, taskId={}", task.getTaskId(), exception);
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
