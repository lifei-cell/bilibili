package com.gary.bilibili.video.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.VideoConstant;
import com.gary.bilibili.video.dto.VideoPublishDTO;
import com.gary.bilibili.video.dto.VideoUpdateDTO;
import com.gary.bilibili.video.entity.Video;
import com.gary.bilibili.video.entity.VideoStats;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.mapper.VideoStatsMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.message.VideoViewMessage;
import com.gary.bilibili.video.model.VideoDetailRow;
import com.gary.bilibili.video.model.VideoListRow;
import com.gary.bilibili.video.model.VideoPage;
import com.gary.bilibili.video.service.VideoBloomFilter;
import com.gary.bilibili.video.service.VideoService;
import com.gary.bilibili.video.service.VideoListCache;
import com.gary.bilibili.video.vo.VideoAuthorVO;
import com.gary.bilibili.video.vo.VideoDetailVO;
import com.gary.bilibili.video.vo.VideoListVO;
import com.gary.bilibili.video.vo.VideoPlayVO;
import com.gary.bilibili.video.vo.VideoPublishVO;
import com.gary.bilibili.video.vo.VideoQualityVO;
import com.gary.bilibili.video.vo.VideoStatsVO;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class VideoServiceImpl implements VideoService {

    private static final Logger log = LoggerFactory.getLogger(VideoServiceImpl.class);

    private final VideoMapper videoMapper;
    private final VideoStatsMapper videoStatsMapper;
    private final VideoTranscodeTaskMapper videoTranscodeTaskMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;
    private final VideoBloomFilter videoBloomFilter;
    private final VideoListCache videoListCache;

    public VideoServiceImpl(VideoMapper videoMapper,
                            VideoStatsMapper videoStatsMapper,
                            VideoTranscodeTaskMapper videoTranscodeTaskMapper,
                            StringRedisTemplate stringRedisTemplate,
                            RocketMQTemplate rocketMQTemplate,
                            ObjectMapper objectMapper,
                            VideoBloomFilter videoBloomFilter,
                            VideoListCache videoListCache) {
        this.videoMapper = videoMapper;
        this.videoStatsMapper = videoStatsMapper;
        this.videoTranscodeTaskMapper = videoTranscodeTaskMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
        this.videoBloomFilter = videoBloomFilter;
        this.videoListCache = videoListCache;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public VideoPublishVO publish(VideoPublishDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        String fileMd5 = request.getFileMd5().toLowerCase(Locale.ROOT);
        Long duplicateCount = videoMapper.selectCount(new LambdaQueryWrapper<Video>()
                .eq(Video::getUserId, userId)
                .eq(Video::getFileMd5, fileMd5)
                .eq(Video::getDeleted, 0));
        if (duplicateCount != null && duplicateCount > 0) {
            throw new BusinessException("视频已提交，请勿重复发布");
        }

        VideoTranscodeTask transcodeTask = videoTranscodeTaskMapper.selectSuccessByFileMd5(fileMd5);
        if (transcodeTask == null || !StringUtils.hasText(transcodeTask.getOutputUrl())) {
            throw new BusinessException("视频仍在转码，请完成转码后再发布");
        }

        Video video = new Video();
        video.setUserId(userId);
        video.setTitle(request.getTitle().trim());
        video.setDescription(request.getDescription());
        video.setCoverUrl(request.getCoverUrl());
        // The original source address is only an API compatibility field. Use
        // the finished task as the source of truth so a client cannot publish
        // an arbitrary external URL as platform content.
        video.setSourceUrl(transcodeTask.getSourceUrl());
        video.setPlayUrl(transcodeTask.getOutputUrl());
        video.setFileMd5(fileMd5);
        video.setFileSize(request.getFileSize());
        video.setDuration(request.getDuration());
        video.setResolution(request.getResolution().trim().toUpperCase(Locale.ROOT));
        video.setCategoryId(request.getCategoryId());
        video.setTags(joinTags(request.getTags()));
        video.setStatus(VideoConstant.STATUS_AUDITING);
        video.setDeleted(0);
        videoMapper.insert(video);

        VideoStats stats = new VideoStats();
        stats.setVideoId(video.getId());
        videoStatsMapper.insert(stats);
        videoBloomFilter.put(video.getId());
        videoListCache.invalidate();

        VideoPublishVO result = new VideoPublishVO();
        result.setVideoId(video.getId());
        result.setStatus(VideoConstant.STATUS_AUDITING);
        return result;
    }

    @Override
    public VideoDetailVO getDetail(Long videoId) {
        assertPossibleVideo(videoId);
        VideoDetailVO cachedDetail = readDetailCache(videoId);
        if (cachedDetail != null) {
            applyRealtimeViewCount(cachedDetail);
            return cachedDetail;
        }

        VideoDetailRow row = videoMapper.selectPublishedDetail(videoId);
        if (row == null) {
            throw new BusinessException("资源不存在");
        }
        VideoDetailVO detail = toDetail(row);
        writeDetailCache(detail);
        applyRealtimeViewCount(detail);
        return detail;
    }

    @Override
    public VideoPlayVO getPlayInfo(Long videoId) {
        assertPossibleVideo(videoId);
        Video video = findPublishedVideo(videoId);
        if (video == null) {
            throw new BusinessException("资源不存在");
        }
        if (!StringUtils.hasText(video.getPlayUrl())) {
            throw new BusinessException("视频转码未完成");
        }

        String qualityName = StringUtils.hasText(video.getResolution())
                ? video.getResolution() : "default";
        VideoQualityVO quality = new VideoQualityVO();
        quality.setQuality(qualityName);
        quality.setUrl(video.getPlayUrl());

        VideoPlayVO result = new VideoPlayVO();
        result.setVideoId(videoId);
        result.setDefaultQuality(qualityName);
        result.setQualities(Collections.singletonList(quality));
        sendViewMessage(videoId);
        return result;
    }

    @Override
    public VideoPage getList(Integer page, Integer size, Long categoryId, String sort) {
        return queryPage(page, size, categoryId, null, normalizeSort(sort));
    }

    @Override
    public VideoPage getUserVideos(Long userId, Integer page, Integer size, String sort) {
        return queryPage(page, size, null, userId, normalizeSort(sort));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long videoId, VideoUpdateDTO request) {
        Video video = loadOwnedVideo(videoId);
        Video update = new Video();
        update.setId(videoId);
        boolean changed = false;

        if (request.getTitle() != null) {
            if (!StringUtils.hasText(request.getTitle())) {
                throw new BusinessException("请求参数错误");
            }
            update.setTitle(request.getTitle().trim());
            changed = true;
        }
        if (request.getDescription() != null) {
            update.setDescription(request.getDescription());
            changed = true;
        }
        if (request.getCoverUrl() != null) {
            update.setCoverUrl(request.getCoverUrl());
            changed = true;
        }
        if (request.getCategoryId() != null) {
            update.setCategoryId(request.getCategoryId());
            changed = true;
        }
        if (request.getTags() != null) {
            update.setTags(joinTags(request.getTags()));
            changed = true;
        }
        if (!changed) {
            throw new BusinessException("请求参数错误");
        }

        videoMapper.updateById(update);
        deleteDetailCache(video.getId());
        videoListCache.invalidate();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long videoId) {
        Video video = loadOwnedVideo(videoId);
        videoMapper.update(null, new LambdaUpdateWrapper<Video>()
                .eq(Video::getId, video.getId())
                .eq(Video::getDeleted, 0)
                .set(Video::getStatus, VideoConstant.STATUS_OFFLINE)
                .set(Video::getDeleted, 1));
        deleteDetailCache(video.getId());
        videoListCache.invalidate();
    }

    private VideoPage queryPage(Integer page,
                                Integer size,
                                Long categoryId,
                                Long userId,
                                String sort) {
        VideoPage cached = videoListCache.get(page, size, categoryId, userId, sort);
        if (cached != null) {
            return cached;
        }
        long offset = (long) (page - 1) * size;
        List<VideoListRow> rows = videoMapper.selectPublishedList(
                categoryId, userId, sort, offset, size);
        List<VideoListVO> records = new ArrayList<>(rows.size());
        for (VideoListRow row : rows) {
            VideoListVO item = new VideoListVO();
            item.setId(row.getId());
            item.setTitle(row.getTitle());
            item.setCoverUrl(row.getCoverUrl());
            item.setDuration(row.getDuration());
            item.setAuthorName(row.getAuthorName());
            item.setViewCount(nullToZero(row.getViewCount()) + getPendingViewCount(row.getId()));
            item.setDanmuCount(nullToZero(row.getDanmuCount()));
            item.setCreateTime(row.getCreateTime());
            records.add(item);
        }

        VideoPage result = new VideoPage();
        result.setRecords(records);
        result.setTotal(nullToZero(videoMapper.countPublishedList(categoryId, userId)));
        videoListCache.put(page, size, categoryId, userId, sort, result);
        return result;
    }

    private Video loadOwnedVideo(Long videoId) {
        Long userId = StpUtil.getLoginIdAsLong();
        Video video = videoMapper.selectOne(new LambdaQueryWrapper<Video>()
                .eq(Video::getId, videoId)
                .eq(Video::getDeleted, 0));
        if (video == null) {
            throw new BusinessException("资源不存在");
        }
        String role = videoMapper.selectUserRole(userId);
        if (!userId.equals(video.getUserId()) && !"admin".equals(role)) {
            throw new BusinessException("无权访问该资源");
        }
        return video;
    }

    private Video findPublishedVideo(Long videoId) {
        return videoMapper.selectOne(new LambdaQueryWrapper<Video>()
                .eq(Video::getId, videoId)
                .eq(Video::getStatus, VideoConstant.STATUS_PUBLISHED)
                .eq(Video::getDeleted, 0));
    }

    private void assertPossibleVideo(Long videoId) {
        if (videoId == null || videoId <= 0 || !videoBloomFilter.mightContain(videoId)) {
            throw new BusinessException("资源不存在");
        }
    }

    private String joinTags(List<String> tags) {
        Set<String> normalizedTags = new LinkedHashSet<>();
        for (String tag : tags) {
            String normalizedTag = tag.trim();
            if (!StringUtils.hasText(normalizedTag) || normalizedTag.contains(",")) {
                throw new BusinessException("请求参数错误");
            }
            normalizedTags.add(normalizedTag);
        }
        String value = String.join(",", normalizedTags);
        if (value.length() > 500) {
            throw new BusinessException("请求参数错误");
        }
        return value;
    }

    private String normalizeSort(String sort) {
        if (!StringUtils.hasText(sort)) {
            return VideoConstant.SORT_DEFAULT;
        }
        String value = sort.toLowerCase(Locale.ROOT);
        if (!VideoConstant.SORT_DEFAULT.equals(value)
                && !VideoConstant.SORT_HOT.equals(value)
                && !VideoConstant.SORT_NEW.equals(value)) {
            throw new BusinessException("请求参数错误");
        }
        return value;
    }

    private VideoDetailVO toDetail(VideoDetailRow row) {
        VideoAuthorVO author = new VideoAuthorVO();
        author.setId(row.getAuthorId());
        author.setNickname(row.getAuthorNickname());
        author.setAvatar(row.getAuthorAvatar());

        VideoStatsVO stats = new VideoStatsVO();
        stats.setViewCount(nullToZero(row.getViewCount()));
        stats.setLikeCount(nullToZero(row.getLikeCount()));
        stats.setCollectCount(nullToZero(row.getCollectCount()));
        stats.setDanmuCount(nullToZero(row.getDanmuCount()));
        stats.setCommentCount(nullToZero(row.getCommentCount()));

        VideoDetailVO detail = new VideoDetailVO();
        detail.setId(row.getId());
        detail.setTitle(row.getTitle());
        detail.setDescription(row.getDescription());
        detail.setCoverUrl(row.getCoverUrl());
        detail.setDuration(row.getDuration());
        detail.setCategoryId(row.getCategoryId());
        detail.setTags(splitTags(row.getTags()));
        detail.setAuthor(author);
        detail.setStats(stats);
        return detail;
    }

    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return Collections.emptyList();
        }
        return List.of(tags.split(","));
    }

    private VideoDetailVO readDetailCache(Long videoId) {
        String key = detailCacheKey(videoId);
        String value;
        try {
            value = stringRedisTemplate.opsForValue().get(key);
        } catch (Exception exception) {
            log.warn("Read video detail cache failed, videoId={}", videoId, exception);
            return null;
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return objectMapper.readValue(value, VideoDetailVO.class);
        } catch (JsonProcessingException exception) {
            log.warn("Parse video detail cache failed, videoId={}", videoId, exception);
            deleteDetailCache(videoId);
            return null;
        }
    }

    private void writeDetailCache(VideoDetailVO detail) {
        try {
            stringRedisTemplate.opsForValue().set(
                    detailCacheKey(detail.getId()),
                    objectMapper.writeValueAsString(detail),
                    Duration.ofMinutes(VideoConstant.DETAIL_CACHE_TTL_MINUTES));
        } catch (Exception exception) {
            log.warn("Write video detail cache failed, videoId={}", detail.getId(), exception);
        }
    }

    private void applyRealtimeViewCount(VideoDetailVO detail) {
        long pendingViewCount = getPendingViewCount(detail.getId());
        detail.getStats().setViewCount(detail.getStats().getViewCount() + pendingViewCount);
    }

    private long getPendingViewCount(Long videoId) {
        Object value;
        try {
            value = stringRedisTemplate.opsForHash().get(
                    VideoConstant.STATS_CACHE_KEY_PREFIX + videoId,
                    VideoConstant.VIEW_COUNT_FIELD);
        } catch (Exception exception) {
            log.warn("Read cached video view count failed, videoId={}", videoId, exception);
            return 0L;
        }
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException exception) {
            log.warn("Invalid cached video view count, videoId={}, value={}", videoId, value);
            return 0L;
        }
    }

    private void sendViewMessage(Long videoId) {
        VideoViewMessage message = new VideoViewMessage();
        message.setVideoId(videoId);
        message.setRequestId(UUID.randomUUID().toString());
        message.setCreateTime(LocalDateTime.now());
        try {
            rocketMQTemplate.convertAndSend(VideoConstant.VIEW_TOPIC, message);
        } catch (Exception exception) {
            log.error("Send video view message failed, videoId={}", videoId, exception);
        }
    }

    private void deleteDetailCache(Long videoId) {
        try {
            stringRedisTemplate.delete(detailCacheKey(videoId));
        } catch (Exception exception) {
            log.warn("Delete video detail cache failed, videoId={}", videoId, exception);
        }
    }

    private String detailCacheKey(Long videoId) {
        return VideoConstant.DETAIL_CACHE_KEY_PREFIX + videoId;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
