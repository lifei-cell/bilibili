package com.gary.bilibili.video.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.VideoConstant;
import com.gary.bilibili.video.entity.Video;
import com.gary.bilibili.video.entity.ContentAuditLog;
import com.gary.bilibili.video.mapper.ContentAuditLogMapper;
import com.gary.bilibili.video.mapper.VideoMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;

@Service
public class VideoPublicationOperationsService {

    private final VideoMapper videoMapper;
    private final VideoBloomFilter videoBloomFilter;
    private final VideoListCache videoListCache;
    private final StringRedisTemplate stringRedisTemplate;
    private final ContentAuditLogMapper auditLogMapper;

    public VideoPublicationOperationsService(VideoMapper videoMapper,
                                             VideoBloomFilter videoBloomFilter,
                                             VideoListCache videoListCache,
                                             StringRedisTemplate stringRedisTemplate,
                                             ContentAuditLogMapper auditLogMapper) {
        this.videoMapper = videoMapper;
        this.videoBloomFilter = videoBloomFilter;
        this.videoListCache = videoListCache;
        this.stringRedisTemplate = stringRedisTemplate;
        this.auditLogMapper = auditLogMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public PublicationResult publish(long videoId) {
        Video video = videoMapper.selectById(videoId);
        if (video == null || Integer.valueOf(1).equals(video.getDeleted())) {
            throw new BusinessException("资源不存在");
        }
        if (!StringUtils.hasText(video.getPlayUrl())) {
            throw new BusinessException("视频转码未完成");
        }
        if (!Integer.valueOf(VideoConstant.STATUS_PUBLISHED).equals(video.getStatus())) {
            int updated = videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                    .eq(Video::getId, videoId)
                    .eq(Video::getDeleted, 0)
                    .set(Video::getStatus, VideoConstant.STATUS_PUBLISHED)
                    .set(Video::getAuditRemark, "Operations override approval")
                    .set(Video::getAuditBy, 0L)
                    .set(Video::getAuditTime, LocalDateTime.now()));
            if (updated != 1) {
                throw new IllegalStateException("Video publication state changed concurrently");
            }
            ContentAuditLog audit = new ContentAuditLog();
            audit.setTargetType("VIDEO");
            audit.setTargetId(videoId);
            audit.setAction("APPROVE");
            audit.setOperatorId(0L);
            audit.setPreviousStatus(video.getStatus());
            audit.setCurrentStatus(VideoConstant.STATUS_PUBLISHED);
            audit.setRemark("Operations token override");
            auditLogMapper.insert(audit);
        }
        videoBloomFilter.put(videoId);
        videoListCache.invalidate();
        stringRedisTemplate.delete(VideoConstant.DETAIL_CACHE_KEY_PREFIX + videoId);
        return new PublicationResult(videoId, VideoConstant.STATUS_PUBLISHED);
    }

    public record PublicationResult(long videoId, int status) {
    }
}
