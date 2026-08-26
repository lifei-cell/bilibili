package com.gary.bilibili.video.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.VideoConstant;
import com.gary.bilibili.video.entity.Video;
import com.gary.bilibili.video.mapper.VideoMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class VideoPublicationOperationsService {

    private final VideoMapper videoMapper;
    private final VideoBloomFilter videoBloomFilter;
    private final VideoListCache videoListCache;
    private final StringRedisTemplate stringRedisTemplate;

    public VideoPublicationOperationsService(VideoMapper videoMapper,
                                             VideoBloomFilter videoBloomFilter,
                                             VideoListCache videoListCache,
                                             StringRedisTemplate stringRedisTemplate) {
        this.videoMapper = videoMapper;
        this.videoBloomFilter = videoBloomFilter;
        this.videoListCache = videoListCache;
        this.stringRedisTemplate = stringRedisTemplate;
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
                    .set(Video::getStatus, VideoConstant.STATUS_PUBLISHED));
            if (updated != 1) {
                throw new IllegalStateException("Video publication state changed concurrently");
            }
        }
        videoBloomFilter.put(videoId);
        videoListCache.invalidate();
        stringRedisTemplate.delete(VideoConstant.DETAIL_CACHE_KEY_PREFIX + videoId);
        return new PublicationResult(videoId, VideoConstant.STATUS_PUBLISHED);
    }

    public record PublicationResult(long videoId, int status) {
    }
}
