package com.gary.bilibili.danmu.service;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.danmu.mapper.DanmuMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;

@Service
public class DanmuWebSocketTicketService {

    private static final String KEY_PREFIX = "danmu:ws-ticket:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final DanmuMapper danmuMapper;
    private final long ttlSeconds;

    public DanmuWebSocketTicketService(StringRedisTemplate redisTemplate,
                                       DanmuMapper danmuMapper,
                                       @Value("${danmu.websocket-ticket.ttl-seconds:30}") long ttlSeconds) {
        this.redisTemplate = redisTemplate;
        this.danmuMapper = danmuMapper;
        this.ttlSeconds = Math.max(5, Math.min(ttlSeconds, 60));
    }

    public IssuedTicket issue(Long videoId) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (!isPublishedVideo(videoId)) {
            throw new BusinessException("视频不存在");
        }
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        String ticket = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redisTemplate.opsForValue().set(key(ticket), userId + ":" + videoId,
                Duration.ofSeconds(ttlSeconds));
        return new IssuedTicket(ticket, ttlSeconds);
    }

    public Long consume(String ticket, Long videoId) {
        if (!StringUtils.hasText(ticket)) {
            return null;
        }
        String value = redisTemplate.opsForValue().getAndDelete(key(ticket));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String[] parts = value.split(":", 2);
        if (parts.length != 2 || !videoId.toString().equals(parts[1])) {
            return null;
        }
        try {
            return Long.valueOf(parts[0]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private boolean isPublishedVideo(Long videoId) {
        Long count = danmuMapper.countPublishedVideo(videoId);
        return count != null && count > 0;
    }

    private String key(String ticket) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(ticket.getBytes(StandardCharsets.UTF_8));
            return KEY_PREFIX + java.util.HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    public record IssuedTicket(String value, long expiresIn) {
    }
}
