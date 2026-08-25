package com.gary.bilibili.video.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.dto.VideoPublishDTO;
import com.gary.bilibili.video.dto.VideoUpdateDTO;
import com.gary.bilibili.video.entity.Video;
import com.gary.bilibili.video.entity.VideoStats;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.mapper.VideoStatsMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.model.VideoDetailRow;
import com.gary.bilibili.video.model.VideoPage;
import com.gary.bilibili.video.service.VideoBloomFilter;
import com.gary.bilibili.video.service.VideoListCache;
import com.gary.bilibili.video.vo.VideoDetailVO;
import com.gary.bilibili.video.vo.VideoPlayVO;
import com.gary.bilibili.video.vo.VideoPublishVO;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class VideoServiceImplTest {

    private VideoMapper videoMapper;
    private VideoStatsMapper videoStatsMapper;
    private VideoTranscodeTaskMapper videoTranscodeTaskMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private HashOperations<String, Object, Object> hashOperations;
    private RocketMQTemplate rocketMQTemplate;
    private VideoBloomFilter videoBloomFilter;
    private VideoListCache videoListCache;
    private VideoServiceImpl videoService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), "video-test"),
                Video.class);
        videoMapper = mock(VideoMapper.class);
        videoStatsMapper = mock(VideoStatsMapper.class);
        videoTranscodeTaskMapper = mock(VideoTranscodeTaskMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        hashOperations = mock(HashOperations.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        videoBloomFilter = mock(VideoBloomFilter.class);
        videoListCache = mock(VideoListCache.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOperations);

        videoService = new VideoServiceImpl(
                videoMapper,
                videoStatsMapper,
                videoTranscodeTaskMapper,
                stringRedisTemplate,
                rocketMQTemplate,
                new ObjectMapper(),
                videoBloomFilter,
                videoListCache);
    }

    @Test
    void shouldCreateAuditingVideoAndInitialStatsWhenPublishing() {
        when(videoMapper.selectCount(any(Wrapper.class))).thenReturn(0L);
        when(videoTranscodeTaskMapper.selectSuccessByFileMd5("d41d8cd98f00b204e9800998ecf8427e"))
                .thenReturn(completedTranscodeTask());
        doAnswer(invocation -> {
            Video video = invocation.getArgument(0);
            video.setId(10001L);
            return 1;
        }).when(videoMapper).insert(any(Video.class));

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            VideoPublishVO result = videoService.publish(buildPublishRequest());

            assertThat(result.getVideoId()).isEqualTo(10001L);
            assertThat(result.getStatus()).isZero();
            verify(videoStatsMapper).insert(any(VideoStats.class));
            verify(videoBloomFilter).put(10001L);
            verify(videoListCache).invalidate();
        }
    }

    @Test
    void shouldRejectPublishingUntilTheVideoHasBeenTranscoded() {
        when(videoMapper.selectCount(any(Wrapper.class))).thenReturn(0L);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThatThrownBy(() -> videoService.publish(buildPublishRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("视频仍在转码，请完成转码后再发布");
        }
    }

    @Test
    void shouldCachePublishedDetailAndOverlayPendingViewCount() {
        when(videoBloomFilter.mightContain(10001L)).thenReturn(true);
        when(valueOperations.get("video:detail:10001")).thenReturn(null);
        when(videoMapper.selectPublishedDetail(10001L)).thenReturn(buildDetailRow());
        when(hashOperations.get("video:stats:10001", "viewCount")).thenReturn("3");

        VideoDetailVO result = videoService.getDetail(10001L);

        assertThat(result.getTitle()).isEqualTo("SpringBoot 视频平台实战");
        assertThat(result.getTags()).containsExactly("SpringBoot", "Vue3");
        assertThat(result.getStats().getViewCount()).isEqualTo(103L);
        verify(valueOperations).set(eq("video:detail:10001"), any(String.class), any());
    }

    @Test
    void shouldReturnListCacheWithoutDatabaseOrPerItemRedisReads() {
        VideoPage cached = new VideoPage();
        cached.setRecords(List.of());
        cached.setTotal(12L);
        when(videoListCache.get(1, 20, null, null, "default")).thenReturn(cached);

        VideoPage result = videoService.getList(1, 20, null, "default");

        assertThat(result).isSameAs(cached);
        verifyNoInteractions(videoMapper);
        verifyNoInteractions(hashOperations);
    }

    @Test
    void shouldReturnDefaultPlayQualityAndSendViewMessage() {
        Video video = new Video();
        video.setId(10001L);
        video.setResolution("1080P");
        video.setPlayUrl("https://minio.example.com/play/10001_1080p.mp4");
        when(videoBloomFilter.mightContain(10001L)).thenReturn(true);
        when(videoMapper.selectOne(any(Wrapper.class))).thenReturn(video);

        VideoPlayVO result = videoService.getPlayInfo(10001L);

        assertThat(result.getDefaultQuality()).isEqualTo("1080P");
        assertThat(result.getQualities()).singleElement()
                .satisfies(item -> assertThat(item.getUrl()).isEqualTo(video.getPlayUrl()));
        verify(rocketMQTemplate).convertAndSend(eq("video-view"), any(Object.class));
    }

    @Test
    void shouldRejectUpdateFromNonAuthor() {
        Video video = new Video();
        video.setId(10001L);
        video.setUserId(1L);
        when(videoMapper.selectOne(any(Wrapper.class))).thenReturn(video);
        when(videoMapper.selectUserRole(2L)).thenReturn("user");

        VideoUpdateDTO request = new VideoUpdateDTO();
        request.setTitle("新标题");
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(2L);

            assertThatThrownBy(() -> videoService.update(10001L, request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("无权访问该资源");
        }
    }

    private VideoPublishDTO buildPublishRequest() {
        VideoPublishDTO request = new VideoPublishDTO();
        request.setTitle("SpringBoot 视频平台实战");
        request.setDescription("从零实现视频平台后端");
        request.setCoverUrl("https://minio.example.com/covers/10001.png");
        request.setSourceUrl("https://minio.example.com/source/demo.mp4");
        request.setFileMd5("d41d8cd98f00b204e9800998ecf8427e");
        request.setFileSize(104857600L);
        request.setDuration(360);
        request.setResolution("1080P");
        request.setCategoryId(1L);
        request.setTags(List.of("SpringBoot", "Vue3"));
        return request;
    }

    private VideoDetailRow buildDetailRow() {
        VideoDetailRow row = new VideoDetailRow();
        row.setId(10001L);
        row.setTitle("SpringBoot 视频平台实战");
        row.setTags("SpringBoot,Vue3");
        row.setAuthorId(1L);
        row.setAuthorNickname("Gary");
        row.setViewCount(100L);
        row.setLikeCount(88L);
        row.setCollectCount(20L);
        row.setDanmuCount(66L);
        row.setCommentCount(12L);
        return row;
    }

    private VideoTranscodeTask completedTranscodeTask() {
        VideoTranscodeTask task = new VideoTranscodeTask();
        task.setSourceUrl("https://minio.example.com/source/demo.mp4");
        task.setOutputUrl("https://minio.example.com/play/demo.mp4");
        return task;
    }
}
