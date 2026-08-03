package com.gary.bilibili.social.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.social.constant.SocialConstant;
import com.gary.bilibili.social.dto.LikeDTO;
import com.gary.bilibili.social.mapper.LikeMapper;
import com.gary.bilibili.social.service.SocialStatsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LikeServiceImplTest {

    private LikeMapper likeMapper;
    private SetOperations<String, String> setOperations;
    private SocialStatsService socialStatsService;
    private LikeServiceImpl likeService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        likeMapper = mock(LikeMapper.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        socialStatsService = mock(SocialStatsService.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        likeService = new LikeServiceImpl(
                likeMapper, stringRedisTemplate, socialStatsService);
    }

    @Test
    void shouldLikePublishedVideoAndIncrementCountOnce() {
        LikeDTO request = buildRequest(1, 10001L);
        when(likeMapper.countPublishedVideo(10001L)).thenReturn(1L);
        when(likeMapper.activate(1L, 1, 10001L)).thenReturn(0);
        when(likeMapper.insertActive(1L, 1, 10001L)).thenReturn(1);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThat(likeService.like(request).getLiked()).isTrue();
        }

        verify(socialStatsService).increment(
                10001L, SocialConstant.LIKE_COUNT_FIELD, 1);
        verify(setOperations).add("like:1:10001", "1");
    }

    @Test
    void shouldNotIncrementCountForRepeatedLike() {
        LikeDTO request = buildRequest(1, 10001L);
        when(likeMapper.countPublishedVideo(10001L)).thenReturn(1L);
        when(likeMapper.activate(1L, 1, 10001L)).thenReturn(0);
        when(likeMapper.insertActive(1L, 1, 10001L)).thenReturn(0);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            likeService.like(request);
        }

        verify(socialStatsService, never()).increment(
                10001L, SocialConstant.LIKE_COUNT_FIELD, 1);
    }

    @Test
    void shouldUpdateCommentLikeCountDirectly() {
        LikeDTO request = buildRequest(2, 80001L);
        when(likeMapper.countVisibleComment(80001L)).thenReturn(1L);
        when(likeMapper.insertActive(1L, 2, 80001L)).thenReturn(1);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
            likeService.like(request);
        }

        verify(likeMapper).incrementCommentLikeCount(80001L, 1);
    }

    @Test
    void shouldRejectMissingTarget() {
        LikeDTO request = buildRequest(1, 10001L);
        when(likeMapper.countPublishedVideo(10001L)).thenReturn(0L);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThatThrownBy(() -> likeService.like(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("资源不存在");
        }
    }

    private LikeDTO buildRequest(Integer targetType, Long targetId) {
        LikeDTO request = new LikeDTO();
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        return request;
    }
}
