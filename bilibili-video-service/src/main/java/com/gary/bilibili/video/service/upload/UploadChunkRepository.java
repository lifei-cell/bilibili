package com.gary.bilibili.video.service.upload;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.dto.UploadChunkDTO;
import com.gary.bilibili.video.entity.FileChunk;
import com.gary.bilibili.video.mapper.FileChunkMapper;
import com.gary.bilibili.video.model.UploadTask;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/** Chunk metadata persistence and complete ordered-set validation. */
@Service
public class UploadChunkRepository {
    private final FileChunkMapper fileChunkMapper;
    public UploadChunkRepository(FileChunkMapper fileChunkMapper) { this.fileChunkMapper = fileChunkMapper; }
    public void saveChunk(UploadChunkDTO request, UploadTask task, String objectName) {
        FileChunk chunk = new FileChunk();
        chunk.setUploadId(task.getUploadId());
        chunk.setUserId(task.getUserId());
        chunk.setFileMd5(task.getFileMd5());
        chunk.setChunkMd5(request.getChunkMd5().toLowerCase(Locale.ROOT));
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

    public FileChunk findChunk(String uploadId, Integer chunkIndex) {
        return fileChunkMapper.selectOne(new LambdaQueryWrapper<FileChunk>()
                .eq(FileChunk::getUploadId, uploadId)
                .eq(FileChunk::getChunkIndex, chunkIndex));
    }

    public FileChunk findLatestChunk(Long userId, String fileMd5) {
        return fileChunkMapper.selectOne(new LambdaQueryWrapper<FileChunk>()
                .eq(FileChunk::getUserId, userId)
                .eq(FileChunk::getFileMd5, fileMd5)
                .orderByDesc(FileChunk::getUpdateTime)
                .last("limit 1"));
    }

    public List<FileChunk> loadAndValidateChunks(UploadTask task) {
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

    public void markChunksCompleted(UploadTask task) {
        fileChunkMapper.update(null, new LambdaUpdateWrapper<FileChunk>()
                .eq(FileChunk::getUploadId, task.getUploadId())
                .eq(FileChunk::getUserId, task.getUserId())
                .set(FileChunk::getStatus, 1));
    }

    public List<Integer> findUploadedChunks(String uploadId, Long userId) {
        List<FileChunk> chunks = fileChunkMapper.selectList(new LambdaQueryWrapper<FileChunk>()
                .select(FileChunk::getChunkIndex)
                .eq(FileChunk::getUploadId, uploadId)
                .eq(FileChunk::getUserId, userId)
                .orderByAsc(FileChunk::getChunkIndex));
        return chunks.stream().map(FileChunk::getChunkIndex).toList();
    }

}
