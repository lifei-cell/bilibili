package com.gary.bilibili.social.task;

import com.gary.bilibili.social.constant.SocialConstant;
import com.gary.bilibili.social.mapper.SocialStatsMapper;
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
public class SocialStatsSyncTask {

    private static final Logger log = LoggerFactory.getLogger(SocialStatsSyncTask.class);
    private static final DefaultRedisScript<Long> COMPLETE_SYNC_SCRIPT = new DefaultRedisScript<>(
            "local left = redis.call('HINCRBY', KEYS[1], ARGV[1], -tonumber(ARGV[2])); "
                    + "if left == 0 then redis.call('HDEL', KEYS[1], ARGV[1]); end; return left;",
            Long.class);
    private static final DefaultRedisScript<Long> CLEAN_EMPTY_SCRIPT = new DefaultRedisScript<>(
            "return redis.call('HDEL', KEYS[1], ARGV[1]);",
            Long.class);
    private static final DefaultRedisScript<Long> CLEAN_PENDING_SCRIPT = new DefaultRedisScript<>(
            "local first = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0'); "
                    + "local second = tonumber(redis.call('HGET', KEYS[1], ARGV[2]) or '0'); "
                    + "local third = tonumber(redis.call('HGET', KEYS[1], ARGV[3]) or '0'); "
                    + "if first == 0 and second == 0 and third == 0 then "
                    + "return redis.call('SREM', KEYS[2], ARGV[4]); end; return 0;",
            Long.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final SocialStatsMapper socialStatsMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public SocialStatsSyncTask(SocialStatsMapper socialStatsMapper,
                               StringRedisTemplate stringRedisTemplate) {
        this.socialStatsMapper = socialStatsMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Scheduled(
            fixedDelayString = "${social.stats.sync-interval:60000}",
            initialDelayString = "${social.stats.sync-initial-delay:60000}")
    public void syncSocialStats() {
        Set<String> videoIds = stringRedisTemplate.opsForSet()
                .members(SocialConstant.STATS_PENDING_KEY);
        if (videoIds == null || videoIds.isEmpty()) {
            return;
        }
        for (String videoIdValue : videoIds) {
            syncVideo(videoIdValue);
        }
    }

    private void syncVideo(String videoIdValue) {
        String lockKey = SocialConstant.STATS_SYNC_LOCK_KEY_PREFIX + videoIdValue;
        String lockValue = UUID.randomUUID().toString();
        try {
            Boolean locked = stringRedisTemplate.opsForValue()
                    .setIfAbsent(lockKey, lockValue, Duration.ofSeconds(30));
            if (!Boolean.TRUE.equals(locked)) {
                return;
            }
            Long videoId = Long.valueOf(videoIdValue);
            boolean changed = false;
            changed |= syncField(videoId, videoIdValue, SocialConstant.LIKE_COUNT_FIELD);
            changed |= syncField(videoId, videoIdValue, SocialConstant.COLLECT_COUNT_FIELD);
            changed |= syncField(videoId, videoIdValue, SocialConstant.COMMENT_COUNT_FIELD);
            stringRedisTemplate.execute(
                    CLEAN_PENDING_SCRIPT,
                    List.of(SocialConstant.STATS_CACHE_KEY_PREFIX + videoId,
                            SocialConstant.STATS_PENDING_KEY),
                    SocialConstant.LIKE_COUNT_FIELD,
                    SocialConstant.COLLECT_COUNT_FIELD,
                    SocialConstant.COMMENT_COUNT_FIELD,
                    videoIdValue);
            if (changed) {
                stringRedisTemplate.delete(SocialConstant.DETAIL_CACHE_KEY_PREFIX + videoId);
            }
        } catch (Exception exception) {
            log.error("Sync social stats failed, videoId={}", videoIdValue, exception);
        } finally {
            try {
                stringRedisTemplate.execute(
                        RELEASE_LOCK_SCRIPT,
                        List.of(lockKey),
                        lockValue);
            } catch (Exception exception) {
                log.warn("Release social stats sync lock failed, videoId={}",
                        videoIdValue, exception);
            }
        }
    }

    private boolean syncField(Long videoId, String videoIdValue, String field) {
        String statsKey = SocialConstant.STATS_CACHE_KEY_PREFIX + videoId;
        Object value = stringRedisTemplate.opsForHash().get(statsKey, field);
        long delta = value == null ? 0L : Long.parseLong(value.toString());
        if (delta == 0) {
            stringRedisTemplate.execute(
                    CLEAN_EMPTY_SCRIPT,
                    List.of(statsKey),
                    field);
            return false;
        }

        int changed;
        if (SocialConstant.LIKE_COUNT_FIELD.equals(field)) {
            changed = socialStatsMapper.incrementLikeCount(videoId, delta);
        } else if (SocialConstant.COLLECT_COUNT_FIELD.equals(field)) {
            changed = socialStatsMapper.incrementCollectCount(videoId, delta);
        } else {
            changed = socialStatsMapper.incrementCommentCount(videoId, delta);
        }
        if (changed == 0) {
            log.warn("Video stats record does not exist, videoId={}, field={}", videoId, field);
            return false;
        }
        stringRedisTemplate.execute(
                COMPLETE_SYNC_SCRIPT,
                List.of(statsKey),
                field,
                Long.toString(delta));
        return true;
    }
}
