package com.gary.bilibili.danmu.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.danmu.dto.DanmuSendDTO;
import com.gary.bilibili.danmu.entity.Danmu;
import com.gary.bilibili.danmu.mapper.DanmuMapper;
import com.gary.bilibili.danmu.message.DanmuPersistMessage;
import com.gary.bilibili.danmu.model.DanmuPage;
import com.gary.bilibili.danmu.model.UserBrief;
import com.gary.bilibili.danmu.netty.DanmuRoomManager;
import com.gary.bilibili.danmu.vo.DanmuSendVO;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DanmuServiceImplTest {

    private DanmuMapper danmuMapper;
    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RocketMQTemplate rocketMQTemplate;
    private DanmuRoomManager roomManager;
    private DanmuServiceImpl danmuService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        danmuMapper = mock(DanmuMapper.class);
        stringRedisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        rocketMQTemplate = mock(RocketMQTemplate.class);
        roomManager = mock(DanmuRoomManager.class);
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        when(stringRedisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(1L);

        danmuService = new DanmuServiceImpl(
                danmuMapper,
                stringRedisTemplate,
                rocketMQTemplate,
                roomManager);
    }

    @Test
    void shouldSendPublishedVideoDanmuThroughMqAndBroadcast() {
        when(danmuMapper.countPublishedVideo(10001L)).thenReturn(1L);
        when(valueOperations.get("danmu:request:1:client-uuid-001")).thenReturn(null);
        when(valueOperations.setIfAbsent(
                eq("danmu:request:1:client-uuid-001"), any(String.class), any()))
                .thenReturn(true);
        UserBrief user = new UserBrief();
        user.setNickname("Gary");
        when(danmuMapper.selectUserBrief(1L)).thenReturn(user);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            DanmuSendVO result = danmuService.send(buildSendRequest());

            assertThat(result.getAccepted()).isTrue();
            assertThat(result.getDanmuId()).isNotNull();
            verify(rocketMQTemplate).convertAndSend(
                    eq("danmu-persist"), any(DanmuPersistMessage.class));
            verify(roomManager).broadcast(any());
        }
    }

    @Test
    void shouldReturnExistingDanmuForDuplicateRequest() {
        when(danmuMapper.countPublishedVideo(10001L)).thenReturn(1L);
        when(valueOperations.get("danmu:request:1:client-uuid-001")).thenReturn("90001");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            DanmuSendVO result = danmuService.send(buildSendRequest());

            assertThat(result.getDanmuId()).isEqualTo(90001L);
            verify(rocketMQTemplate, never()).convertAndSend(any(String.class), any(Object.class));
            verify(roomManager, never()).broadcast(any());
        }
    }

    @Test
    void shouldRejectBlankDanmuWithManualErrorMessage() {
        DanmuSendDTO request = buildSendRequest();
        request.setContent("   ");

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThatThrownBy(() -> danmuService.send(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("弹幕内容不能为空");
        }
    }

    @Test
    void shouldRejectRequestWhenRateLimitIsReached() {
        when(danmuMapper.countPublishedVideo(10001L)).thenReturn(1L);
        when(valueOperations.get("danmu:request:1:client-uuid-001")).thenReturn(null);
        when(stringRedisTemplate.execute(
                any(RedisScript.class), anyList(), any(Object[].class))).thenReturn(0L);

        try (MockedStatic<StpUtil> stpUtil = mockStatic(StpUtil.class)) {
            stpUtil.when(StpUtil::getLoginIdAsLong).thenReturn(1L);

            assertThatThrownBy(() -> danmuService.send(buildSendRequest()))
                    .isInstanceOf(BusinessException.class)
                    .hasMessage("弹幕发送过于频繁");
        }
    }

    @Test
    void shouldReturnPagedDanmuInVideoTimeOrder() {
        Danmu danmu = new Danmu();
        danmu.setId(90001L);
        danmu.setUserId(1L);
        danmu.setContent("这个地方讲得很清楚");
        danmu.setVideoTime(120);
        danmu.setSendTime(LocalDateTime.now());
        when(danmuMapper.selectDanmuList(10001L, 60, 180, 0L, 500))
                .thenReturn(List.of(danmu));
        when(danmuMapper.countDanmuList(10001L, 60, 180)).thenReturn(1L);

        DanmuPage result = danmuService.getList(10001L, 60, 180, 1, 500);

        assertThat(result.getTotal()).isEqualTo(1L);
        assertThat(result.getRecords()).singleElement()
                .satisfies(item -> assertThat(item.getVideoTime()).isEqualTo(120));
    }

    private DanmuSendDTO buildSendRequest() {
        DanmuSendDTO request = new DanmuSendDTO();
        request.setVideoId(10001L);
        request.setContent("这个地方讲得很清楚");
        request.setColor("#FFFFFF");
        request.setPosition(0);
        request.setFontSize(16);
        request.setVideoTime(120);
        request.setRequestId("client-uuid-001");
        return request;
    }
}
