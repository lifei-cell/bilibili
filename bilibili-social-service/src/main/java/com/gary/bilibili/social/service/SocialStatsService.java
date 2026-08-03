package com.gary.bilibili.social.service;

import com.gary.bilibili.social.constant.SocialConstant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SocialStatsService {

    private static final Logger log = LoggerFactory.getLogger(SocialStatsService.class);
    private static final DefaultRedisScript<Long> INCREMENT_COUNT_SCRIPT = new DefaultRedisScript<>(
            "local value = redis.call('HINCRBY', KEYS[1], ARGV[1], ARGV[2]); "
                    + "redis.call('SADD', KEYS[2], ARGV[3]); return value;",
            Long.class);

    private final StringRedisTemplate stringRedisTemplate;

    public SocialStatsService(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void increment(Long videoId, String field, long delta) {
        if (delta == 0) {
            return;
        }
        try {
            stringRedisTemplate.execute(
                    INCREMENT_COUNT_SCRIPT,
                    List.of(SocialConstant.STATS_CACHE_KEY_PREFIX + videoId,
                            SocialConstant.STATS_PENDING_KEY),
                    field,
                    Long.toString(delta),
                    videoId.toString());
        } catch (Exception exception) {
            log.error("Increment cached social stats failed, videoId={}, field={}, delta={}",
                    videoId, field, delta, exception);
        }
    }
}
