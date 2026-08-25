package com.gary.bilibili.video.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.dto.FileCheckDTO;
import com.gary.bilibili.video.dto.FileChunkUploadDTO;
import com.gary.bilibili.video.dto.FileMergeDTO;
import com.gary.bilibili.video.dto.UploadCheckDTO;
import com.gary.bilibili.video.dto.UploadChunkDTO;
import com.gary.bilibili.video.dto.UploadMergeDTO;
import com.gary.bilibili.video.entity.FileChunk;
import com.gary.bilibili.video.entity.Video;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.FileChunkMapper;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.model.UploadTask;
import com.gary.bilibili.video.service.UploadService;
import com.gary.bilibili.video.vo.FileCheckVO;
import com.gary.bilibili.video.vo.FileMergeVO;
import com.gary.bilibili.video.vo.UploadCheckVO;
import com.gary.bilibili.video.vo.UploadChunkVO;
import com.gary.bilibili.video.vo.UploadMergeVO;
import com.gary.bilibili.video.vo.UploadProgressVO;
import com.gary.bilibili.video.vo.VideoTranscodeStatusVO;
import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class UploadServiceImpl implements UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadServiceImpl.class);
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
    private final VideoMapper videoMapper;
    private final VideoTranscodeTaskMapper videoTranscodeTaskMapper;
    private final MinioClient minioClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final String publicEndpoint;
    private final String videoBucket;
    private final String tempBucket;

    public UploadServiceImpl(FileChunkMapper fileChunkMapper,
                             VideoMapper videoMapper,
                             VideoTranscodeTaskMapper videoTranscodeTaskMapper,
                             MinioClient minioClient,
                             StringRedisTemplate stringRedisTemplate,
                             ObjectMapper objectMapper,
                             @Value("${minio.public-endpoint}") String publicEndpoint,
                             @Value("${minio.video-bucket}") String videoBucket,
                             @Value("${minio.temp-bucket}") String tempBucket) {
        this.fileChunkMapper = fileChunkMapper;
        this.videoMapper = videoMapper;
        this.videoTranscodeTaskMapper = videoTranscodeTaskMapper;
        this.minioClient = minioClient;
        this.stringRedisTemplate = stringRedisTemplate;
        this.objectMapper = objectMapper;
        this.publicEndpoint = publicEndpoint;
        this.videoBucket = videoBucket;
        this.tempBucket = tempBucket;
    }

    @Override
    public UploadCheckVO check(UploadCheckDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        checkRateLimit(UploadConstant.CHECK_RATE_LIMIT_KEY_PREFIX + userId, 60, 60);
        String fileMd5 = normalizeMd5(request.getFileMd5());
        validateChunkPlan(request.getFileSize(), request.getTotalChunks());

        Video video = findInstantVideo(fileMd5);
        if (video != null) {
            UploadCheckVO result = new UploadCheckVO();
            result.setInstant(true);
            result.setVideoId(video.getId());
            result.setSourceUrl(video.getSourceUrl());
            return result;
        }

        UploadTask task = findReusableTask(userId, fileMd5, request);
        saveTask(task);

        UploadCheckVO result = new UploadCheckVO();
        result.setInstant(false);
        result.setUploadId(task.getUploadId());
        result.setUploadedChunks(findUploadedChunks(task.getUploadId(), userId));
        return result;
    }

    @Override
    public UploadChunkVO uploadChunk(UploadChunkDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        checkRateLimit(UploadConstant.CHUNK_RATE_LIMIT_KEY_PREFIX + userId, 5, 1);
        UploadTask task = loadTask(request.getUploadId());
        validateTask(task, userId, request.getFileMd5(), request.getTotalChunks());
        validateChunk(request, task);

        FileChunk existing = findChunk(request.getUploadId(), request.getChunkIndex());
        if (existing != null) {
            if (!existing.getUserId().equals(userId)
                    || !request.getChunkMd5().equalsIgnoreCase(existing.getChunkMd5())) {
                throw new BusinessException("分片校验失败");
            }
            refreshTask(task);
            return buildChunkResult(request);
        }

        verifyChunkMd5(request);
        String objectName = task.getUploadId() + "/" + request.getChunkIndex();
        putChunk(request, objectName);
        saveChunk(request, task, objectName);
        refreshTask(task);
        return buildChunkResult(request);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UploadMergeVO merge(UploadMergeDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        UploadTask task = loadTask(request.getUploadId());
        validateTask(task, userId, request.getFileMd5(), request.getTotalChunks());
        if (!task.getFileName().equals(request.getFileName())) {
            throw new BusinessException("上传任务信息不一致");
        }

        String lockKey = UploadConstant.MERGE_LOCK_KEY_PREFIX + task.getUploadId();
        String lockValue = UUID.randomUUID().toString();
        Boolean locked = stringRedisTemplate.opsForValue()
                .setIfAbsent(lockKey, lockValue, Duration.ofMinutes(5));
        if (!Boolean.TRUE.equals(locked)) {
            throw new BusinessException("视频正在合并，请稍后重试");
        }

        try {
            List<FileChunk> chunks = loadAndValidateChunks(task);
            String sourceObjectName = buildSourceObjectName(task);
            composeChunks(chunks, sourceObjectName);
            String sourceUrl = buildObjectUrl(videoBucket, sourceObjectName);
            saveTranscodeTask(task, sourceUrl, sourceObjectName);
            markChunksCompleted(task);
            refreshTask(task);

            UploadMergeVO result = new UploadMergeVO();
            result.setSourceUrl(sourceUrl);
            result.setFileMd5(task.getFileMd5());
            result.setTranscodeTaskId(task.getUploadId());
            result.setTranscodeStatus("waiting");
            return result;
        } finally {
            releaseLock(lockKey, lockValue);
        }
    }

    @Override
    public UploadProgressVO getProgress(String uploadId) {
        Long userId = StpUtil.getLoginIdAsLong();
        UploadTask task = loadTask(uploadId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException("上传任务不存在");
        }

        List<Integer> uploadedChunks = findUploadedChunks(uploadId, userId);
        UploadProgressVO result = new UploadProgressVO();
        result.setUploadId(uploadId);
        result.setUploadedChunks(uploadedChunks);
        result.setTotalChunks(task.getTotalChunks());
        result.setPercent(uploadedChunks.size() * 100 / task.getTotalChunks());
        return result;
    }

    @Override
    public VideoTranscodeStatusVO getTranscodeStatus(String taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        VideoTranscodeTask task = videoTranscodeTaskMapper.selectByTaskId(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BusinessException("转码任务不存在");
        }

        VideoTranscodeStatusVO result = new VideoTranscodeStatusVO();
        result.setTaskId(task.getTaskId());
        result.setStatus(toTranscodeState(task.getStatus()));
        result.setRetryCount(task.getRetryCount() == null ? 0 : task.getRetryCount());
        result.setNextRetryTime(task.getNextRetryTime());
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_SUCCESS).equals(task.getStatus())) {
            result.setOutputUrl(task.getOutputUrl());
        }
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_FAILED).equals(task.getStatus())) {
            result.setErrorMessage(StringUtils.hasText(task.getErrorMessage())
                    ? task.getErrorMessage() : "视频转码失败");
        }
        return result;
    }

    @Override
    public FileCheckVO checkFile(FileCheckDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        checkRateLimit(UploadConstant.CHECK_RATE_LIMIT_KEY_PREFIX + userId, 60, 60);
        String fileMd5 = normalizeMd5(request.getMd5());
        Video video = findInstantVideo(fileMd5);

        FileCheckVO result = new FileCheckVO();
        if (video != null) {
            result.setExist(true);
            result.setVideoId(video.getId());
            return result;
        }

        result.setExist(false);
        FileChunk chunk = findLatestChunk(userId, fileMd5);
        if (chunk != null) {
            result.setUploadedChunks(findUploadedChunks(chunk.getUploadId(), userId));
        }
        return result;
    }

    @Override
    public UploadChunkVO uploadFileChunk(FileChunkUploadDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        validateChunkPlan(request.getFileSize(), request.getTotalChunks());

        UploadCheckDTO checkRequest = new UploadCheckDTO();
        checkRequest.setFileMd5(request.getMd5());
        checkRequest.setFileName(request.getFileName());
        checkRequest.setFileSize(request.getFileSize());
        checkRequest.setTotalChunks(request.getTotalChunks());
        UploadTask task = findReusableTask(userId, normalizeMd5(request.getMd5()), checkRequest);
        saveTask(task);

        UploadChunkDTO chunkRequest = new UploadChunkDTO();
        chunkRequest.setUploadId(task.getUploadId());
        chunkRequest.setFileMd5(request.getMd5());
        chunkRequest.setChunkMd5(request.getChunkMd5());
        chunkRequest.setChunkIndex(request.getChunkIndex());
        chunkRequest.setChunkSize(request.getFile().getSize());
        chunkRequest.setTotalChunks(request.getTotalChunks());
        chunkRequest.setFile(request.getFile());
        return uploadChunk(chunkRequest);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileMergeVO mergeFile(FileMergeDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        String fileMd5 = normalizeMd5(request.getMd5());
        String uploadId = stringRedisTemplate.opsForValue().get(fileTaskKey(userId, fileMd5));
        UploadTask task = loadTask(uploadId);
        if (task == null) {
            throw new BusinessException("上传任务不存在");
        }

        UploadMergeDTO mergeRequest = new UploadMergeDTO();
        mergeRequest.setUploadId(task.getUploadId());
        mergeRequest.setFileMd5(fileMd5);
        mergeRequest.setFileName(request.getFileName());
        mergeRequest.setTotalChunks(request.getTotalChunks());
        UploadMergeVO mergeResult = merge(mergeRequest);

        FileMergeVO result = new FileMergeVO();
        result.setVideoUrl(mergeResult.getSourceUrl());
        return result;
    }

    @Override
    public UploadProgressVO getFileProgress(String fileMd5) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (!StringUtils.hasText(fileMd5) || !fileMd5.matches("(?i)^[a-f0-9]{32}$")) {
            throw new BusinessException("文件 MD5 格式错误");
        }
        FileChunk chunk = findLatestChunk(userId, normalizeMd5(fileMd5));
        if (chunk == null) {
            throw new BusinessException("上传任务不存在");
        }

        List<Integer> uploadedChunks = findUploadedChunks(chunk.getUploadId(), userId);
        UploadProgressVO result = new UploadProgressVO();
        result.setUploadId(chunk.getUploadId());
        result.setUploadedChunks(uploadedChunks);
        result.setTotalChunks(chunk.getTotalChunks());
        result.setPercent(uploadedChunks.size() * 100 / chunk.getTotalChunks());
        return result;
    }

    private UploadTask findReusableTask(Long userId, String fileMd5, UploadCheckDTO request) {
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

    private Video findInstantVideo(String fileMd5) {
        return videoMapper.selectOne(new LambdaQueryWrapper<Video>()
                .eq(Video::getFileMd5, fileMd5)
                .eq(Video::getStatus, 1)
                .eq(Video::getDeleted, 0)
                .isNotNull(Video::getSourceUrl)
                .orderByDesc(Video::getId)
                .last("limit 1"));
    }

    private void validateChunkPlan(Long fileSize, Integer totalChunks) {
        long expectedTotalChunks = (fileSize + UploadConstant.CHUNK_SIZE - 1) / UploadConstant.CHUNK_SIZE;
        if (expectedTotalChunks != totalChunks) {
            throw new BusinessException("分片总数与文件大小不匹配");
        }
    }

    private void validateTask(UploadTask task, Long userId, String fileMd5, Integer totalChunks) {
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException("上传任务不存在");
        }
        if (!task.getFileMd5().equals(normalizeMd5(fileMd5))
                || !task.getTotalChunks().equals(totalChunks)) {
            throw new BusinessException("上传任务信息不一致");
        }
    }

    private void validateChunk(UploadChunkDTO request, UploadTask task) {
        if (request.getChunkIndex() >= task.getTotalChunks()) {
            throw new BusinessException("分片序号非法");
        }
        long expectedSize = request.getChunkIndex() == task.getTotalChunks() - 1
                ? task.getFileSize() - UploadConstant.CHUNK_SIZE * (task.getTotalChunks() - 1L)
                : UploadConstant.CHUNK_SIZE;
        if (request.getFile().isEmpty()
                || request.getChunkSize() != request.getFile().getSize()
                || request.getFile().getSize() != expectedSize) {
            throw new BusinessException("分片大小错误");
        }
    }

    private void verifyChunkMd5(UploadChunkDTO request) {
        try (InputStream inputStream = request.getFile().getInputStream()) {
            String actualMd5 = DigestUtils.md5DigestAsHex(inputStream);
            if (!actualMd5.equalsIgnoreCase(request.getChunkMd5())) {
                throw new BusinessException("分片校验失败");
            }
        } catch (IOException exception) {
            throw new BusinessException("分片读取失败");
        }
    }

    private void putChunk(UploadChunkDTO request, String objectName) {
        try (InputStream inputStream = request.getFile().getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(tempBucket)
                    .object(objectName)
                    .stream(inputStream, request.getFile().getSize(), -1)
                    .contentType("application/octet-stream")
                    .build());
        } catch (Exception exception) {
            log.error("Upload chunk to MinIO failed, uploadId={}, chunkIndex={}",
                    request.getUploadId(), request.getChunkIndex(), exception);
            throw new BusinessException("分片上传失败");
        }
    }

    private void saveChunk(UploadChunkDTO request, UploadTask task, String objectName) {
        FileChunk chunk = new FileChunk();
        chunk.setUploadId(task.getUploadId());
        chunk.setUserId(task.getUserId());
        chunk.setFileMd5(task.getFileMd5());
        chunk.setChunkMd5(normalizeMd5(request.getChunkMd5()));
        chunk.setChunkIndex(request.getChunkIndex());
        chunk.setChunkSize(request.getChunkSize());
        chunk.setTotalChunks(task.getTotalChunks());
        chunk.setFileName(task.getFileName());
        chunk.setObjectName(objectName);
        chunk.setStatus(0);
        chunk.setExpireTime(LocalDateTime.now().plusHours(UploadConstant.TASK_EXPIRE_HOURS));
        try {
            fileChunkMapper.insert(chunk);
        } catch (DuplicateKeyException exception) {
            FileChunk existing = findChunk(task.getUploadId(), request.getChunkIndex());
            if (existing == null || !request.getChunkMd5().equalsIgnoreCase(existing.getChunkMd5())) {
                throw new BusinessException("分片校验失败");
            }
        }
    }

    private FileChunk findChunk(String uploadId, Integer chunkIndex) {
        return fileChunkMapper.selectOne(new LambdaQueryWrapper<FileChunk>()
                .eq(FileChunk::getUploadId, uploadId)
                .eq(FileChunk::getChunkIndex, chunkIndex));
    }

    private FileChunk findLatestChunk(Long userId, String fileMd5) {
        return fileChunkMapper.selectOne(new LambdaQueryWrapper<FileChunk>()
                .eq(FileChunk::getUserId, userId)
                .eq(FileChunk::getFileMd5, fileMd5)
                .orderByDesc(FileChunk::getUpdateTime)
                .last("limit 1"));
    }

    private UploadChunkVO buildChunkResult(UploadChunkDTO request) {
        UploadChunkVO result = new UploadChunkVO();
        result.setUploadId(request.getUploadId());
        result.setChunkIndex(request.getChunkIndex());
        result.setUploaded(true);
        return result;
    }

    private List<FileChunk> loadAndValidateChunks(UploadTask task) {
        List<FileChunk> chunks = fileChunkMapper.selectList(new LambdaQueryWrapper<FileChunk>()
                .eq(FileChunk::getUploadId, task.getUploadId())
                .eq(FileChunk::getUserId, task.getUserId())
                .orderByAsc(FileChunk::getChunkIndex));
        if (chunks.size() != task.getTotalChunks()) {
            throw new BusinessException("分片未全部上传");
        }
        for (int index = 0; index < chunks.size(); index++) {
            FileChunk chunk = chunks.get(index);
            if (chunk.getChunkIndex() != index
                    || !chunk.getFileMd5().equals(task.getFileMd5())
                    || !chunk.getTotalChunks().equals(task.getTotalChunks())) {
                throw new BusinessException("分片信息不完整");
            }
        }
        return chunks;
    }

    private void composeChunks(List<FileChunk> chunks, String sourceObjectName) {
        List<ComposeSource> sources = new ArrayList<>();
        for (FileChunk chunk : chunks) {
            sources.add(ComposeSource.builder()
                    .bucket(tempBucket)
                    .object(chunk.getObjectName())
                    .build());
        }
        try {
            if (!objectExists(videoBucket, sourceObjectName)) {
                minioClient.composeObject(ComposeObjectArgs.builder()
                        .bucket(videoBucket)
                        .object(sourceObjectName)
                        .sources(sources)
                        .build());
            }
        } catch (Exception exception) {
            log.error("Merge chunks in MinIO failed, objectName={}", sourceObjectName, exception);
            throw new BusinessException("分片合并失败");
        }
    }

    private boolean objectExists(String bucket, String objectName) throws Exception {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName)
                    .build());
            return true;
        } catch (ErrorResponseException exception) {
            if ("NoSuchKey".equals(exception.errorResponse().code())
                    || "NoSuchObject".equals(exception.errorResponse().code())) {
                return false;
            }
            throw exception;
        }
    }

    private void saveTranscodeTask(UploadTask task, String sourceUrl, String sourceObjectName) {
        VideoTranscodeTask existing = videoTranscodeTaskMapper.selectByTaskId(task.getUploadId());
        if (existing == null) {
            VideoTranscodeTask transcodeTask = new VideoTranscodeTask();
            transcodeTask.setTaskId(task.getUploadId());
            transcodeTask.setUserId(task.getUserId());
            transcodeTask.setFileMd5(task.getFileMd5());
            transcodeTask.setFileName(task.getFileName());
            transcodeTask.setFileSize(task.getFileSize());
            transcodeTask.setSourceUrl(sourceUrl);
            transcodeTask.setSourceObjectName(sourceObjectName);
            transcodeTask.setStatus(UploadConstant.TRANSCODE_STATUS_PENDING);
            transcodeTask.setRetryCount(0);
            videoTranscodeTaskMapper.insert(transcodeTask);
            return;
        }
        if (!Integer.valueOf(UploadConstant.TRANSCODE_STATUS_SUCCESS).equals(existing.getStatus())) {
            videoTranscodeTaskMapper.resetPending(
                    task.getUploadId(), sourceUrl, sourceObjectName,
                    task.getFileName(), task.getFileSize());
        }
    }

    private String toTranscodeState(Integer status) {
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_SUCCESS).equals(status)) {
            return UploadConstant.TRANSCODE_STATE_COMPLETED;
        }
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_FAILED).equals(status)) {
            return UploadConstant.TRANSCODE_STATE_FAILED;
        }
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_PROCESSING).equals(status)
                || Integer.valueOf(UploadConstant.TRANSCODE_STATUS_DISPATCHED).equals(status)) {
            return UploadConstant.TRANSCODE_STATE_PROCESSING;
        }
        return UploadConstant.TRANSCODE_STATE_WAITING;
    }

    private void markChunksCompleted(UploadTask task) {
        fileChunkMapper.update(null, new LambdaUpdateWrapper<FileChunk>()
                .eq(FileChunk::getUploadId, task.getUploadId())
                .eq(FileChunk::getUserId, task.getUserId())
                .set(FileChunk::getStatus, 1));
    }

    private List<Integer> findUploadedChunks(String uploadId, Long userId) {
        List<FileChunk> chunks = fileChunkMapper.selectList(new LambdaQueryWrapper<FileChunk>()
                .select(FileChunk::getChunkIndex)
                .eq(FileChunk::getUploadId, uploadId)
                .eq(FileChunk::getUserId, userId)
                .orderByAsc(FileChunk::getChunkIndex));
        return chunks.stream().map(FileChunk::getChunkIndex).toList();
    }

    private void saveTask(UploadTask task) {
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

    private void refreshTask(UploadTask task) {
        saveTask(task);
    }

    private UploadTask loadTask(String uploadId) {
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

    private void releaseLock(String lockKey, String lockValue) {
        stringRedisTemplate.execute(RELEASE_LOCK_SCRIPT,
                Collections.singletonList(lockKey), lockValue);
    }

    private void checkRateLimit(String key, int limit, int windowSeconds) {
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

    private String buildSourceObjectName(UploadTask task) {
        return "source/" + task.getFileMd5() + getSafeExtension(task.getFileName());
    }

    private String getSafeExtension(String fileName) {
        int index = fileName.lastIndexOf('.');
        if (index < 0 || index == fileName.length() - 1) {
            return "";
        }
        String extension = fileName.substring(index + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,10}") ? "." + extension : "";
    }

    private String buildObjectUrl(String bucket, String objectName) {
        String endpoint = publicEndpoint.endsWith("/")
                ? publicEndpoint.substring(0, publicEndpoint.length() - 1)
                : publicEndpoint;
        return endpoint + "/" + bucket + "/" + objectName;
    }

    private String normalizeMd5(String md5) {
        return md5.toLowerCase(Locale.ROOT);
    }

    private String taskKey(String uploadId) {
        return UploadConstant.TASK_KEY_PREFIX + uploadId;
    }

    private String fileTaskKey(Long userId, String fileMd5) {
        return UploadConstant.FILE_TASK_KEY_PREFIX + userId + ":" + fileMd5;
    }
}
