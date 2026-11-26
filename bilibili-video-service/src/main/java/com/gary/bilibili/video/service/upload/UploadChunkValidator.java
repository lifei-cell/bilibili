package com.gary.bilibili.video.service.upload;

import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.dto.UploadChunkDTO;
import com.gary.bilibili.video.model.UploadTask;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

/** Upload identity, chunk geometry and content hash validation. */
@Service
public class UploadChunkValidator {

    public void validateChunkPlan(Long fileSize, Integer totalChunks) {
        long expectedTotalChunks = (fileSize + UploadConstant.CHUNK_SIZE - 1) / UploadConstant.CHUNK_SIZE;
        if (expectedTotalChunks != totalChunks) {
            throw new BusinessException("分片总数与文件大小不匹配");
        }
    }

    public void validateTask(UploadTask task, Long userId, String fileMd5, Integer totalChunks) {
        if (task == null || !task.getUserId().equals(userId)) {
            throw new BusinessException("上传任务不存在");
        }
        if (!task.getFileMd5().equals(normalizeMd5(fileMd5))
                || !task.getTotalChunks().equals(totalChunks)) {
            throw new BusinessException("上传任务信息不一致");
        }
    }

    public void validateChunk(UploadChunkDTO request, UploadTask task) {
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

    public void verifyChunkMd5(UploadChunkDTO request) {
        try (InputStream inputStream = request.getFile().getInputStream()) {
            String actualMd5 = DigestUtils.md5DigestAsHex(inputStream);
            if (!actualMd5.equalsIgnoreCase(request.getChunkMd5())) {
                throw new BusinessException("分片校验失败");
            }
        } catch (IOException exception) {
            throw new BusinessException("分片读取失败");
        }
    }

    public String normalizeMd5(String md5) {
        return md5.toLowerCase(Locale.ROOT);
    }

}
