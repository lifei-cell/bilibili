package com.gary.bilibili.video.service.upload;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.dto.UploadMergeDTO;
import com.gary.bilibili.video.entity.FileChunk;
import com.gary.bilibili.video.model.UploadTask;
import com.gary.bilibili.video.vo.UploadMergeVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Transactional composition workflow shared by both upload API families. */
@Service
public class UploadMergeService {
    private final UploadSessionManager sessions;
    private final UploadChunkValidator validator;
    private final UploadChunkRepository chunksRepository;
    private final UploadStorage storage;
    private final UploadTranscodeTasks transcode;
    public UploadMergeService(UploadSessionManager sessions, UploadChunkValidator validator,
                              UploadChunkRepository chunksRepository, UploadStorage storage,
                              UploadTranscodeTasks transcode) {
        this.sessions = sessions;
        this.validator = validator;
        this.chunksRepository = chunksRepository;
        this.storage = storage;
        this.transcode = transcode;
    }
    @Transactional(rollbackFor = Exception.class)
    public UploadMergeVO merge(UploadMergeDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        UploadTask task = sessions.loadTask(request.getUploadId());
        validator.validateTask(task, userId, request.getFileMd5(), request.getTotalChunks());
        if (!task.getFileName().equals(request.getFileName())) {
            throw new BusinessException("上传任务信息不一致");
        }

        String lockKey = UploadConstant.MERGE_LOCK_KEY_PREFIX + task.getUploadId();
        String lockValue = sessions.acquireMergeLock(task.getUploadId());

        try {
            List<FileChunk> chunks = chunksRepository.loadAndValidateChunks(task);
            String sourceObjectName = storage.buildSourceObjectName(task);
            storage.composeChunks(chunks, sourceObjectName);
            String sourceUrl = storage.sourceUrl(sourceObjectName);
            transcode.saveTranscodeTask(task, sourceUrl, sourceObjectName);
            chunksRepository.markChunksCompleted(task);
            sessions.refreshTask(task);

            UploadMergeVO result = new UploadMergeVO();
            result.setSourceUrl(sourceUrl);
            result.setFileMd5(task.getFileMd5());
            result.setTranscodeTaskId(task.getUploadId());
            result.setTranscodeStatus("waiting");
            return result;
        } finally {
            sessions.releaseLock(lockKey, lockValue);
        }
    }

}
