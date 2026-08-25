package com.gary.bilibili.user.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.user.config.AuthSessionProperties;
import com.gary.bilibili.user.exception.AuthenticationExpiredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;

@Service
public class RefreshSessionService {

    private static final Logger log = LoggerFactory.getLogger(RefreshSessionService.class);
    private static final String TOKEN_KEY_PREFIX = "auth:refresh:token:";
    private static final String USER_KEY_PREFIX = "auth:refresh:user:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AuthSessionProperties properties;

    public RefreshSessionService(StringRedisTemplate redisTemplate,
                                 ObjectMapper objectMapper,
                                 AuthSessionProperties properties) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public IssuedRefreshToken issue(Long userId, String terminal) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String digest = digest(rawToken);
        RefreshPrincipal principal = new RefreshPrincipal(userId, normalizeTerminal(terminal));
        Duration ttl = Duration.ofSeconds(properties.getRefreshTokenTtlSeconds());
        try {
            redisTemplate.opsForValue().set(tokenKey(digest),
                    objectMapper.writeValueAsString(principal), ttl);
            redisTemplate.opsForSet().add(userKey(userId), digest);
            redisTemplate.expire(userKey(userId), ttl);
            return new IssuedRefreshToken(rawToken, properties.getRefreshTokenTtlSeconds());
        } catch (Exception exception) {
            redisTemplate.delete(tokenKey(digest));
            throw new IllegalStateException("创建刷新会话失败", exception);
        }
    }

    public RefreshPrincipal consume(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            throw new AuthenticationExpiredException();
        }
        String digest = digest(rawToken);
        String value = redisTemplate.opsForValue().getAndDelete(tokenKey(digest));
        if (!StringUtils.hasText(value)) {
            throw new AuthenticationExpiredException();
        }
        try {
            RefreshPrincipal principal = objectMapper.readValue(value, RefreshPrincipal.class);
            redisTemplate.opsForSet().remove(userKey(principal.userId()), digest);
            return principal;
        } catch (Exception exception) {
            log.warn("Discard malformed refresh session, digest={}", digest, exception);
            throw new AuthenticationExpiredException();
        }
    }

    public void revoke(String rawToken) {
        if (!StringUtils.hasText(rawToken)) {
            return;
        }
        String digest = digest(rawToken);
        String value = redisTemplate.opsForValue().getAndDelete(tokenKey(digest));
        if (!StringUtils.hasText(value)) {
            return;
        }
        try {
            RefreshPrincipal principal = objectMapper.readValue(value, RefreshPrincipal.class);
            redisTemplate.opsForSet().remove(userKey(principal.userId()), digest);
        } catch (Exception exception) {
            log.warn("Failed to remove refresh token from user index, digest={}", digest, exception);
        }
    }

    public void revokeAll(Long userId) {
        String userKey = userKey(userId);
        Set<String> digests = redisTemplate.opsForSet().members(userKey);
        if (digests != null && !digests.isEmpty()) {
            redisTemplate.delete(digests.stream().map(this::tokenKey).toList());
        }
        redisTemplate.delete(userKey);
    }

    private String digest(String rawToken) {
        try {
            byte[] hashed = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private String normalizeTerminal(String terminal) {
        if (!StringUtils.hasText(terminal)) {
            return "web";
        }
        String value = terminal.trim();
        return value.length() <= 32 ? value : value.substring(0, 32);
    }

    private String tokenKey(String digest) {
        return TOKEN_KEY_PREFIX + digest;
    }

    private String userKey(Long userId) {
        return USER_KEY_PREFIX + userId;
    }

    public record IssuedRefreshToken(String value, long maxAgeSeconds) {
    }

    public record RefreshPrincipal(Long userId, String terminal) {
    }
}
