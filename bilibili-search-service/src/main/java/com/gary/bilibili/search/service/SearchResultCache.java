package com.gary.bilibili.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.search.model.SearchPage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;

@Service
public class SearchResultCache {

    private static final Logger log = LoggerFactory.getLogger(SearchResultCache.class);
    private static final String KEY_PREFIX = "search:result:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public SearchResultCache(StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper,
                             @Value("${search.result-cache.ttl-seconds:5}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttl = Duration.ofSeconds(Math.max(1, Math.min(ttlSeconds, 30)));
    }

    public SearchPage get(String keyword, Long categoryId, String sort, Integer page, Integer size) {
        try {
            String value = redisTemplate.opsForValue().get(key(keyword, categoryId, sort, page, size));
            return StringUtils.hasText(value) ? objectMapper.readValue(value, SearchPage.class) : null;
        } catch (Exception exception) {
            log.warn("Read search result cache failed", exception);
            return null;
        }
    }

    public void put(String keyword, Long categoryId, String sort,
                    Integer page, Integer size, SearchPage result) {
        try {
            redisTemplate.opsForValue().set(key(keyword, categoryId, sort, page, size),
                    objectMapper.writeValueAsString(result), ttl);
        } catch (Exception exception) {
            log.warn("Write search result cache failed", exception);
        }
    }

    private String key(String keyword, Long categoryId, String sort, Integer page, Integer size) {
        String source = keyword + '|' + (categoryId == null ? 0 : categoryId)
                + '|' + sort + '|' + page + '|' + size;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }
}
