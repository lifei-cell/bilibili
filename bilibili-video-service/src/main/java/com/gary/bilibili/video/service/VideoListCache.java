package com.gary.bilibili.video.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.video.model.VideoPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Service
public class VideoListCache {

    private static final Logger log = LoggerFactory.getLogger(VideoListCache.class);
    private static final String VERSION_KEY = "video:list-cache:version";
    private static final String KEY_PREFIX = "video:list-cache:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public VideoListCache(StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          @Value("${video.list-cache.ttl-seconds:5}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(Math.max(1, Math.min(ttlSeconds, 30)));
    }

    public VideoPage get(Integer page, Integer size, Long categoryId, Long userId, String sort) {
        try {
            String value = redisTemplate.opsForValue().get(key(page, size, categoryId, userId, sort));
            return StringUtils.hasText(value) ? objectMapper.readValue(value, VideoPage.class) : null;
        } catch (Exception exception) {
            log.warn("Read video list cache failed", exception);
            return null;
        }
    }

    public void put(Integer page, Integer size, Long categoryId, Long userId,
                    String sort, VideoPage result) {
        try {
            redisTemplate.opsForValue().set(key(page, size, categoryId, userId, sort),
                    objectMapper.writeValueAsString(result), ttl);
        } catch (Exception exception) {
            log.warn("Write video list cache failed", exception);
        }
    }

    public void invalidate() {
        try {
            redisTemplate.opsForValue().increment(VERSION_KEY);
        } catch (Exception exception) {
            log.warn("Invalidate video list cache failed", exception);
        }
    }

    private String key(Integer page, Integer size, Long categoryId, Long userId, String sort) {
        String version = redisTemplate.opsForValue().get(VERSION_KEY);
        return KEY_PREFIX + (version == null ? "0" : version)
                + ':' + page + ':' + size
                + ':' + (categoryId == null ? 0 : categoryId)
                + ':' + (userId == null ? 0 : userId)
                + ':' + sort;
    }
}
