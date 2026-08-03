package com.gary.bilibili.social.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.social.mapper.FollowMapper;
import com.gary.bilibili.social.model.UserPage;
import com.gary.bilibili.social.model.UserRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FollowServiceImplTest {

    private FollowMapper followMapper;
    private SetOperations<String, String> setOperations;
    private FollowServiceImpl followService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        followMapper = mock(FollowMapper.class);
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
        followService = new FollowServiceImpl(followMapper, stringRedisTemplate);
    }

    @Test
    void shouldCreateFollowRelationAndCacheIt() {
        when(followMapper.countEnabledUser(2L)).thenReturn(1L);
        when(followMapper.activate(1L, 2L)).thenReturn(0);
        when(followMapper.insertActive(1L, 2L)).thenReturn(1);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThat(followService.follow(2L).getFollowed()).isTrue();
        }

        verify(setOperations).add("follow:following:1", "2");
    }

    @Test
    void shouldKeepRepeatedFollowIdempotent() {
        when(followMapper.countEnabledUser(2L)).thenReturn(1L);
        when(followMapper.activate(1L, 2L)).thenReturn(0);
        when(followMapper.insertActive(1L, 2L)).thenReturn(0);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThat(followService.follow(2L).getFollowed()).isTrue();
        }
    }

    @Test
    void shouldRejectFollowingSelf() {
        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThatThrownBy(() -> followService.follow(1L))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("不能关注自己");
        }

        verify(followMapper, never()).insertActive(1L, 1L);
    }

    @Test
    void shouldReturnPagedFollowingUsers() {
        UserRow row = new UserRow();
        row.setId(2L);
        row.setNickname("Gary");
        when(followMapper.countEnabledUser(1L)).thenReturn(1L);
        when(followMapper.selectFollowing(1L, 0L, 20)).thenReturn(List.of(row));
        when(followMapper.countFollowing(1L)).thenReturn(1L);

        UserPage result = followService.getFollowing(1L, 1, 20);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement()
                .satisfies(item -> assertThat(item.getNickname()).isEqualTo("Gary"));
    }
}
