package com.gary.bilibili.danmu.task;

import com.gary.bilibili.danmu.constant.DanmuConstant;
import com.gary.bilibili.danmu.mapper.DanmuMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Component
public class DanmuStatsSyncTask {

    private static final Logger log = LoggerFactory.getLogger(DanmuStatsSyncTask.class);
    private static final DefaultRedisScript<Long> COMPLETE_SYNC_SCRIPT = new DefaultRedisScript<>(
            "local left = redis.call('HINCRBY', KEYS[1], ARGV[1], -tonumber(ARGV[2])); "
                    + "if left <= 0 then redis.call('HDEL', KEYS[1], ARGV[1]); "
                    + "redis.call('SREM', KEYS[2], ARGV[3]); return 0 end; return left;",
            Long.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final DanmuMapper danmuMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public DanmuStatsSyncTask(DanmuMapper danmuMapper,
                              StringRedisTemplate stringRedisTemplate) {
        this.danmuMapper = danmuMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Scheduled(
            fixedDelayString = "${danmu.stats.sync-interval:60000}",
            initialDelayString = "${danmu.stats.sync-initial-delay:60000}")
    public void syncDanmuCount() {
        Set<String> videoIds = stringRedisTemplate.opsForSet()
                .members(DanmuConstant.STATS_PENDING_KEY);
        if (videoIds == null || videoIds.isEmpty()) {
            return;
        }
        for (String videoIdValue : videoIds) {
            syncVideo(videoIdValue);
        }
    }

    private void syncVideo(String videoIdValue) {
        String lockKey = DanmuConstant.STATS_SYNC_LOCK_KEY_PREFIX + videoIdValue;
        String lockValue = UUID.randomUUID().toString();
        try {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(30));
            if (!Boolean.TRUE.equals(locked)) {
                return;
            }
            Long videoId = Long.valueOf(videoIdValue);
            String statsKey = DanmuConstant.STATS_CACHE_KEY_PREFIX + videoId;
            Object value = stringRedisTemplate.opsForHash().get(
                    statsKey, DanmuConstant.DANMU_COUNT_FIELD);
            long count = value == null ? 0L : Long.parseLong(value.toString());
            if (count <= 0) {
                stringRedisTemplate.opsForSet().remove(
                        DanmuConstant.STATS_PENDING_KEY, videoIdValue);
                return;
            }
            if (danmuMapper.incrementDanmuCount(videoId, count) == 0) {
                log.warn("Video stats record does not exist, videoId={}", videoId);
                return;
            }
            stringRedisTemplate.execute(
                    COMPLETE_SYNC_SCRIPT,
                    List.of(statsKey, DanmuConstant.STATS_PENDING_KEY),
                    DanmuConstant.DANMU_COUNT_FIELD,
                    Long.toString(count),
                    videoIdValue);
            stringRedisTemplate.delete(DanmuConstant.DETAIL_CACHE_KEY_PREFIX + videoId);
        } catch (Exception exception) {
            log.error("Sync video danmu count failed, videoId={}", videoIdValue, exception);
        } finally {
            try {
                stringRedisTemplate.execute(
                        RELEASE_LOCK_SCRIPT,
                        List.of(lockKey),
                        lockValue);
            } catch (Exception exception) {
                log.warn("Release danmu stats sync lock failed, videoId={}", videoIdValue, exception);
            }
        }
    }
}
