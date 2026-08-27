package com.gary.bilibili.video.task;

import com.gary.bilibili.video.constant.VideoConstant;
import com.gary.bilibili.video.mapper.VideoStatsMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
@ConditionalOnProperty(name = "video.stats.sync-enabled", havingValue = "true", matchIfMissing = true)
public class VideoStatsSyncTask {

    private static final Logger log = LoggerFactory.getLogger(VideoStatsSyncTask.class);
    private static final DefaultRedisScript<Long> COMPLETE_SYNC_SCRIPT = new DefaultRedisScript<>(
            "local left = redis.call('HINCRBY', KEYS[1], ARGV[1], -tonumber(ARGV[2])); "
                    + "if left <= 0 then redis.call('HDEL', KEYS[1], ARGV[1]); "
                    + "redis.call('SREM', KEYS[2], ARGV[3]); return 0 end; return left;",
            Long.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final VideoStatsMapper videoStatsMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public VideoStatsSyncTask(VideoStatsMapper videoStatsMapper,
                              StringRedisTemplate stringRedisTemplate) {
        this.videoStatsMapper = videoStatsMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Scheduled(
            fixedDelayString = "${video.stats.sync-interval:60000}",
            initialDelayString = "${video.stats.sync-initial-delay:60000}")
    public void syncViewCount() {
        Set<String> videoIds = stringRedisTemplate.opsForSet()
                .members(VideoConstant.STATS_PENDING_KEY);
        if (videoIds == null || videoIds.isEmpty()) {
            return;
        }
        for (String videoIdValue : videoIds) {
            syncVideo(videoIdValue);
        }
    }

    private void syncVideo(String videoIdValue) {
        String lockKey = VideoConstant.STATS_SYNC_LOCK_KEY_PREFIX + videoIdValue;
        String lockValue = UUID.randomUUID().toString();
        try {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(30));
            if (!Boolean.TRUE.equals(locked)) {
                return;
            }
            Long videoId = Long.valueOf(videoIdValue);
            String statsKey = VideoConstant.STATS_CACHE_KEY_PREFIX + videoId;
            Object value = stringRedisTemplate.opsForHash().get(
                    statsKey, VideoConstant.VIEW_COUNT_FIELD);
            long count = value == null ? 0L : Long.parseLong(value.toString());
            if (count <= 0) {
                stringRedisTemplate.opsForSet().remove(
                        VideoConstant.STATS_PENDING_KEY, videoIdValue);
                return;
            }
            if (videoStatsMapper.incrementViewCount(videoId, count) == 0) {
                log.warn("Video stats record does not exist, videoId={}", videoId);
                return;
            }
            stringRedisTemplate.execute(
                    COMPLETE_SYNC_SCRIPT,
                    List.of(statsKey, VideoConstant.STATS_PENDING_KEY),
                    VideoConstant.VIEW_COUNT_FIELD,
                    Long.toString(count),
                    videoIdValue);
            stringRedisTemplate.delete(VideoConstant.DETAIL_CACHE_KEY_PREFIX + videoId);
        } catch (Exception exception) {
            log.error("Sync video view count failed, videoId={}", videoIdValue, exception);
        } finally {
            try {
                stringRedisTemplate.execute(
                        RELEASE_LOCK_SCRIPT,
                        List.of(lockKey),
                        lockValue);
            } catch (Exception exception) {
                log.warn("Release video stats sync lock failed, videoId={}", videoIdValue, exception);
            }
        }
    }
}
