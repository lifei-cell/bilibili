package com.gary.bilibili.video.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
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
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.model.UploadTask;
import com.gary.bilibili.video.service.UploadService;
import com.gary.bilibili.video.service.upload.UploadSessionManager;
import com.gary.bilibili.video.service.upload.UploadChunkValidator;
import com.gary.bilibili.video.service.upload.UploadChunkRepository;
import com.gary.bilibili.video.service.upload.UploadStorage;
import com.gary.bilibili.video.service.upload.UploadMergeService;
import com.gary.bilibili.video.service.upload.UploadTranscodeTasks;
import com.gary.bilibili.video.vo.FileCheckVO;
import com.gary.bilibili.video.vo.FileMergeVO;
import com.gary.bilibili.video.vo.UploadCheckVO;
import com.gary.bilibili.video.vo.UploadChunkVO;
import com.gary.bilibili.video.vo.UploadMergeVO;
import com.gary.bilibili.video.vo.UploadProgressVO;
import com.gary.bilibili.video.vo.VideoTranscodeStatusVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/** API orchestration; infrastructure and merge transactions belong to dedicated collaborators. */
@Service
public class UploadServiceImpl implements UploadService {
    private final VideoMapper videoMapper;
    private final UploadSessionManager sessions;
    private final UploadChunkValidator validator;
    private final UploadChunkRepository chunks;
    private final UploadStorage storage;
    private final UploadMergeService merger;
    private final UploadTranscodeTasks transcode;

    public UploadServiceImpl(VideoMapper videoMapper, UploadSessionManager sessions,
                             UploadChunkValidator validator, UploadChunkRepository chunks,
                             UploadStorage storage, UploadMergeService merger, UploadTranscodeTasks transcode) {
        this.videoMapper = videoMapper;
        this.sessions = sessions;
        this.validator = validator;
        this.chunks = chunks;
        this.storage = storage;
        this.merger = merger;
        this.transcode = transcode;
    }

    @Override
    public UploadMergeVO merge(UploadMergeDTO request) { return merger.merge(request); }

    @Override
    public VideoTranscodeStatusVO getTranscodeStatus(String taskId) { return transcode.getTranscodeStatus(taskId); }

    @Override
    public UploadCheckVO check(UploadCheckDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        sessions.checkRateLimit(UploadConstant.CHECK_RATE_LIMIT_KEY_PREFIX + userId, 60, 60);
        String fileMd5 = validator.normalizeMd5(request.getFileMd5());
        validator.validateChunkPlan(request.getFileSize(), request.getTotalChunks());

        Video video = findInstantVideo(fileMd5);
        if (video != null) {
            UploadCheckVO result = new UploadCheckVO();
            result.setInstant(true);
            result.setVideoId(video.getId());
            result.setSourceUrl(video.getSourceUrl());
            return result;
        }

        UploadTask task = sessions.findReusableTask(userId, fileMd5, request);
        sessions.saveTask(task);

        UploadCheckVO result = new UploadCheckVO();
        result.setInstant(false);
        result.setUploadId(task.getUploadId());
        result.setUploadedChunks(chunks.findUploadedChunks(task.getUploadId(), userId));
        return result;
    }

    @Override
    public UploadChunkVO uploadChunk(UploadChunkDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        sessions.checkRateLimit(UploadConstant.CHUNK_RATE_LIMIT_KEY_PREFIX + userId, 5, 1);
        UploadTask task = sessions.loadTask(request.getUploadId());
        validator.validateTask(task, userId, request.getFileMd5(), request.getTotalChunks());
        validator.validateChunk(request, task);

        FileChunk existing = chunks.findChunk(request.getUploadId(), request.getChunkIndex());
        if (existing != null) {
            if (!existing.getUserId().equals(userId)
                    || !request.getChunkMd5().equalsIgnoreCase(existing.getChunkMd5())) {
                throw new BusinessException("分片校验失败");
            }
            sessions.refreshTask(task);
            return buildChunkResult(request);
        }

        validator.verifyChunkMd5(request);
        String objectName = task.getUploadId() + "/" + request.getChunkIndex();
        storage.putChunk(request, objectName);
        chunks.saveChunk(request, task, objectName);
        sessions.refreshTask(task);
        return buildChunkResult(request);
    }

    @Override
    public UploadProgressVO getProgress(String uploadId) {
        Long userId = StpUtil.getLoginIdAsLong();
        UploadTask task = sessions.loadTask(uploadId);
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException("上传任务不存在");
        }

        List<Integer> uploadedChunks = chunks.findUploadedChunks(uploadId, userId);
        UploadProgressVO result = new UploadProgressVO();
        result.setUploadId(uploadId);
        result.setUploadedChunks(uploadedChunks);
        result.setTotalChunks(task.getTotalChunks());
        result.setPercent(uploadedChunks.size() * 100 / task.getTotalChunks());
        return result;
    }

    @Override
    public FileCheckVO checkFile(FileCheckDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        sessions.checkRateLimit(UploadConstant.CHECK_RATE_LIMIT_KEY_PREFIX + userId, 60, 60);
        String fileMd5 = validator.normalizeMd5(request.getMd5());
        Video video = findInstantVideo(fileMd5);

        FileCheckVO result = new FileCheckVO();
        if (video != null) {
            result.setExist(true);
            result.setVideoId(video.getId());
            return result;
        }

        result.setExist(false);
        FileChunk chunk = chunks.findLatestChunk(userId, fileMd5);
        if (chunk != null) {
            result.setUploadedChunks(chunks.findUploadedChunks(chunk.getUploadId(), userId));
        }
        return result;
    }

    @Override
    public UploadChunkVO uploadFileChunk(FileChunkUploadDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        validator.validateChunkPlan(request.getFileSize(), request.getTotalChunks());

        UploadCheckDTO checkRequest = new UploadCheckDTO();
        checkRequest.setFileMd5(request.getMd5());
        checkRequest.setFileName(request.getFileName());
        checkRequest.setFileSize(request.getFileSize());
        checkRequest.setTotalChunks(request.getTotalChunks());
        UploadTask task = sessions.findReusableTask(userId, validator.normalizeMd5(request.getMd5()), checkRequest);
        sessions.saveTask(task);

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
    public FileMergeVO mergeFile(FileMergeDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        String fileMd5 = validator.normalizeMd5(request.getMd5());
        UploadTask task = sessions.findByFile(userId, fileMd5);
        if (task == null) {
            throw new BusinessException("上传任务不存在");
        }

        UploadMergeDTO mergeRequest = new UploadMergeDTO();
        mergeRequest.setUploadId(task.getUploadId());
        mergeRequest.setFileMd5(fileMd5);
        mergeRequest.setFileName(request.getFileName());
        mergeRequest.setTotalChunks(request.getTotalChunks());
        UploadMergeVO mergeResult = merger.merge(mergeRequest);

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
        FileChunk chunk = chunks.findLatestChunk(userId, validator.normalizeMd5(fileMd5));
        if (chunk == null) {
            throw new BusinessException("上传任务不存在");
        }

        List<Integer> uploadedChunks = chunks.findUploadedChunks(chunk.getUploadId(), userId);
        UploadProgressVO result = new UploadProgressVO();
        result.setUploadId(chunk.getUploadId());
        result.setUploadedChunks(uploadedChunks);
        result.setTotalChunks(chunk.getTotalChunks());
        result.setPercent(uploadedChunks.size() * 100 / chunk.getTotalChunks());
        return result;
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

    private UploadChunkVO buildChunkResult(UploadChunkDTO request) {
        UploadChunkVO result = new UploadChunkVO();
        result.setUploadId(request.getUploadId());
        result.setChunkIndex(request.getChunkIndex());
        result.setUploaded(true);
        return result;
    }
}
