package com.gary.bilibili.video.service.upload;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.dto.UploadCheckDTO;
import com.gary.bilibili.video.entity.FileChunk;
import com.gary.bilibili.video.mapper.FileChunkMapper;
import com.gary.bilibili.video.model.UploadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.UUID;

/** Redis session lifetime, resumable task lookup and token-checked coordination. */
@Service
public class UploadSessionManager {
    private static final Logger log = LoggerFactory.getLogger(UploadSessionManager.class);
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local start = tonumber(ARGV[3]) - tonumber(ARGV[2]); "
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, start); "
                    + "if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[1]) then return 0 end; "
                    + "redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4]); "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[2]); return 1;",
            Long.class);
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final FileChunkMapper fileChunkMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    public UploadSessionManager(FileChunkMapper fileChunkMapper, StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.fileChunkMapper = fileChunkMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
    }
    public UploadTask findReusableTask(Long userId, String fileMd5, UploadCheckDTO request) {
        String cachedUploadId = stringRedisTemplate.opsForValue().get(fileTaskKey(userId, fileMd5));
        UploadTask cachedTask = loadTask(cachedUploadId);
        if (matches(cachedTask, userId, fileMd5, request)) {
            return cachedTask;
        }

        FileChunk chunk = fileChunkMapper.selectOne(new LambdaQueryWrapper<FileChunk>()
                .eq(FileChunk::getUserId, userId)
                .eq(FileChunk::getFileMd5, fileMd5)
                .eq(FileChunk::getFileName, request.getFileName())
                .eq(FileChunk::getTotalChunks, request.getTotalChunks())
                .eq(FileChunk::getStatus, 0)
                .gt(FileChunk::getExpireTime, LocalDateTime.now())
                .orderByDesc(FileChunk::getUpdateTime)
                .last("limit 1"));
        if (chunk != null) {
            return buildTask(chunk.getUploadId(), userId, fileMd5, request);
        }
        return buildTask("up_" + UUID.randomUUID().toString().replace("-", ""),
                userId, fileMd5, request);
    }

    private UploadTask buildTask(String uploadId, Long userId, String fileMd5, UploadCheckDTO request) {
        UploadTask task = new UploadTask();
        task.setUploadId(uploadId);
        task.setUserId(userId);
        task.setFileMd5(fileMd5);
        task.setFileName(request.getFileName());
        task.setFileSize(request.getFileSize());
        task.setTotalChunks(request.getTotalChunks());
        return task;
    }

    private boolean matches(UploadTask task, Long userId, String fileMd5, UploadCheckDTO request) {
        return task != null
                && task.getUserId().equals(userId)
                && task.getFileMd5().equals(fileMd5)
                && task.getFileName().equals(request.getFileName())
                && task.getFileSize().equals(request.getFileSize())
                && task.getTotalChunks().equals(request.getTotalChunks());
    }

    public void saveTask(UploadTask task) {
        try {
            Duration ttl = Duration.ofHours(UploadConstant.TASK_EXPIRE_HOURS);
            stringRedisTemplate.opsForValue().set(taskKey(task.getUploadId()),
                    objectMapper.writeValueAsString(task), ttl);
            stringRedisTemplate.opsForValue().set(fileTaskKey(task.getUserId(), task.getFileMd5()),
                    task.getUploadId(), ttl);
        } catch (JsonProcessingException exception) {
            log.error("Serialize upload task failed, uploadId={}", task.getUploadId(), exception);
            throw new BusinessException("上传任务创建失败");
        }
    }

    public void refreshTask(UploadTask task) {
        saveTask(task);
    }

    public UploadTask loadTask(String uploadId) {
        if (!StringUtils.hasText(uploadId)) {
            return null;
        }
        String value = stringRedisTemplate.opsForValue().get(taskKey(uploadId));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, UploadTask.class);
        } catch (JsonProcessingException exception) {
            log.warn("Deserialize upload task failed, uploadId={}", uploadId, exception);
            stringRedisTemplate.delete(taskKey(uploadId));
            return null;
        }
    }

    public void releaseLock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT,
                Collections.singletonList(lockKey), lockValue);
    }

    public void checkRateLimit(String key, int limit, int windowSeconds) {
        long now = System.currentTimeMillis();
        Long allowed = stringRedisTemplate.execute(RATE_LIMIT_SCRIPT,
                Collections.singletonList(key),
                String.valueOf(limit),
                String.valueOf(windowSeconds * 1000L),
                String.valueOf(now),
                now + ":" + UUID.randomUUID());
        if (!Long.valueOf(1L).equals(allowed)) {
            throw new BusinessException("请求过于频繁，请稍后再试");
        }
    }

    private String taskKey(String uploadId) {
        return UploadConstant.TASK_KEY_PREFIX + uploadId;
    }

    private String fileTaskKey(Long userId, String fileMd5) {
        return UploadConstant.FILE_TASK_KEY_PREFIX + userId + ":" + fileMd5;
    }
    public UploadTask findByFile(Long userId, String fileMd5) {
        return loadTask(stringRedisTemplate.opsForValue().get(fileTaskKey(userId, fileMd5)));
    }

    public String acquireMergeLock(String uploadId) {
        String token = UUID.randomUUID().toString();
        if (!Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(
                UploadConstant.MERGE_LOCK_KEY_PREFIX + uploadId, token, Duration.ofMinutes(5)))) {
            throw new BusinessException("视频正在合并，请稍后重试");
        }
        return token;
    }
}
