package com.gary.bilibili.danmu.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.common.reliability.OutboxEventService;
import com.gary.bilibili.danmu.constant.DanmuConstant;
import com.gary.bilibili.danmu.dto.DanmuSendDTO;
import com.gary.bilibili.danmu.entity.Danmu;
import com.gary.bilibili.danmu.mapper.DanmuMapper;
import com.gary.bilibili.danmu.message.DanmuPersistMessage;
import com.gary.bilibili.danmu.model.DanmuPage;
import com.gary.bilibili.danmu.model.DanmuTimeCount;
import com.gary.bilibili.danmu.model.UserBrief;
import com.gary.bilibili.danmu.netty.DanmuBroadcastPublisher;
import com.gary.bilibili.danmu.service.DanmuService;
import com.gary.bilibili.danmu.vo.DanmuBroadcastVO;
import com.gary.bilibili.danmu.vo.DanmuCountVO;
import com.gary.bilibili.danmu.vo.DanmuListVO;
import com.gary.bilibili.danmu.vo.DanmuSendVO;
import com.gary.bilibili.danmu.vo.DanmuTimeCountVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class DanmuServiceImpl implements DanmuService {

    private static final Logger log = LoggerFactory.getLogger(DanmuServiceImpl.class);
    private static final DefaultRedisScript<Long> RATE_LIMIT_SCRIPT = new DefaultRedisScript<>(
            "local start = tonumber(ARGV[3]) - tonumber(ARGV[2]); "
                    + "redis.call('ZREMRANGEBYSCORE', KEYS[1], 0, start); "
                    + "if redis.call('ZCARD', KEYS[1]) >= tonumber(ARGV[1]) then return 0 end; "
                    + "redis.call('ZADD', KEYS[1], ARGV[3], ARGV[4]); "
                    + "redis.call('PEXPIRE', KEYS[1], ARGV[2]); return 1;",
            Long.class);
    private static final DefaultRedisScript<Long> INCREMENT_COUNT_SCRIPT = new DefaultRedisScript<>(
            "local count = redis.call('HINCRBY', KEYS[1], ARGV[1], 1); "
                    + "redis.call('SADD', KEYS[2], ARGV[2]); return count;",
            Long.class);
    private static final DefaultRedisScript<Long> DELETE_REQUEST_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final DanmuMapper danmuMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final OutboxEventService outboxEventService;
    private final DanmuBroadcastPublisher broadcastPublisher;

    public DanmuServiceImpl(DanmuMapper danmuMapper,
                            StringRedisTemplate stringRedisTemplate,
                            OutboxEventService outboxEventService,
                            DanmuBroadcastPublisher broadcastPublisher) {
        this.danmuMapper = danmuMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.outboxEventService = outboxEventService;
        this.broadcastPublisher = broadcastPublisher;
    }

    @Override
    public DanmuPage getList(Long videoId,
                             Integer startTime,
                             Integer endTime,
                             Integer page,
                             Integer size) {
        validateTimeRange(startTime, endTime);
        long offset = (long) (page - 1) * size;
        List<Danmu> danmus = danmuMapper.selectDanmuList(
                videoId, startTime, endTime, offset, size);
        List<DanmuListVO> records = new ArrayList<>(danmus.size());
        for (Danmu danmu : danmus) {
            records.add(toListVO(danmu));
        }

        DanmuPage result = new DanmuPage();
        result.setRecords(records);
        result.setTotal(nullToZero(danmuMapper.countDanmuList(videoId, startTime, endTime)));
        return result;
    }

    @Override
    public DanmuSendVO send(DanmuSendDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        if (!StringUtils.hasText(request.getContent())) {
            throw new BusinessException("弹幕内容不能为空");
        }
        String content = request.getContent().trim();
        if (content.codePointCount(0, content.length()) > 200) {
            throw new BusinessException("弹幕内容不能超过 200 字");
        }
        if (!isPublishedVideo(request.getVideoId())) {
            throw new BusinessException("视频不存在");
        }

        String requestId = normalizeRequestId(request.getRequestId());
        Long existingDanmuId = findRequestDanmuId(userId, requestId);
        if (existingDanmuId != null) {
            return buildSendResult(existingDanmuId);
        }
        checkRateLimit(userId);

        Long danmuId = IdWorker.getId();
        if (!reserveRequest(userId, requestId, danmuId)) {
            Long reservedDanmuId = findRequestDanmuId(userId, requestId);
            if (reservedDanmuId != null) {
                return buildSendResult(reservedDanmuId);
            }
            throw new BusinessException("弹幕发送失败，请稍后重试");
        }

        LocalDateTime sendTime = LocalDateTime.now();
        DanmuPersistMessage message = buildPersistMessage(danmuId, userId, request, sendTime);
        DanmuBroadcastVO broadcast = buildBroadcast(message);
        try {
            outboxEventService.appendStandalone(
                    "danmu", danmuId.toString(), "DANMU_ACCEPTED",
                    DanmuConstant.PERSIST_TOPIC, message);
        } catch (Exception exception) {
            releaseRequest(userId, requestId, danmuId);
            log.error("Append danmu persist outbox event failed, danmuId={}", danmuId, exception);
            throw new BusinessException("弹幕发送失败，请稍后重试");
        }

        incrementDanmuCount(request.getVideoId());
        broadcastPublisher.publish(broadcast);
        return buildSendResult(danmuId);
    }

    @Override
    public DanmuCountVO getCount(Long videoId) {
        List<DanmuTimeCount> counts = danmuMapper.selectTimeDistributed(videoId);
        List<DanmuTimeCountVO> distributed = new ArrayList<>(counts.size());
        for (DanmuTimeCount count : counts) {
            DanmuTimeCountVO item = new DanmuTimeCountVO();
            item.setTime(count.getTime());
            item.setCount(nullToZero(count.getCount()));
            distributed.add(item);
        }

        DanmuCountVO result = new DanmuCountVO();
        result.setTotal(nullToZero(danmuMapper.countByVideo(videoId)));
        result.setTimeDistributed(distributed);
        return result;
    }

    private void validateTimeRange(Integer startTime, Integer endTime) {
        if (startTime != null && startTime < 0
                || endTime != null && endTime < 0
                || startTime != null && endTime != null && startTime > endTime) {
            throw new BusinessException("请求参数错误");
        }
    }

    private boolean isPublishedVideo(Long videoId) {
        Long count = danmuMapper.countPublishedVideo(videoId);
        return count != null && count > 0;
    }

    private void checkRateLimit(Long userId) {
        long now = System.currentTimeMillis();
        Long allowed = stringRedisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                Collections.singletonList(DanmuConstant.RATE_LIMIT_KEY_PREFIX + userId),
                Integer.toString(DanmuConstant.RATE_LIMIT_COUNT),
                Long.toString(Duration.ofSeconds(DanmuConstant.RATE_LIMIT_WINDOW_SECONDS).toMillis()),
                Long.toString(now),
                now + ":" + UUID.randomUUID());
        if (!Long.valueOf(1L).equals(allowed)) {
            throw new BusinessException("弹幕发送过于频繁");
        }
    }

    private Long findRequestDanmuId(Long userId, String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        String value = stringRedisTemplate.opsForValue().get(requestKey(userId, requestId));
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value);
        } catch (NumberFormatException exception) {
            stringRedisTemplate.delete(requestKey(userId, requestId));
            return null;
        }
    }

    private boolean reserveRequest(Long userId, String requestId, Long danmuId) {
        if (!StringUtils.hasText(requestId)) {
            return true;
        }
        Boolean reserved = stringRedisTemplate.opsForValue().setIfAbsent(
                requestKey(userId, requestId),
                danmuId.toString(),
                Duration.ofSeconds(DanmuConstant.REQUEST_EXPIRE_SECONDS));
        return Boolean.TRUE.equals(reserved);
    }

    private void releaseRequest(Long userId, String requestId, Long danmuId) {
        if (!StringUtils.hasText(requestId)) {
            return;
        }
        try {
            stringRedisTemplate.execute(
                    DELETE_REQUEST_SCRIPT,
                    Collections.singletonList(requestKey(userId, requestId)),
                    danmuId.toString());
        } catch (Exception exception) {
            log.warn("Release danmu request id failed, userId={}, requestId={}",
                    userId, requestId, exception);
        }
    }

    private void incrementDanmuCount(Long videoId) {
        try {
            stringRedisTemplate.execute(
                    INCREMENT_COUNT_SCRIPT,
                    List.of(DanmuConstant.STATS_CACHE_KEY_PREFIX + videoId,
                            DanmuConstant.STATS_PENDING_KEY),
                    DanmuConstant.DANMU_COUNT_FIELD,
                    videoId.toString());
        } catch (Exception exception) {
            log.error("Increment cached danmu count failed, videoId={}", videoId, exception);
        }
    }

    private DanmuPersistMessage buildPersistMessage(Long danmuId,
                                                     Long userId,
                                                     DanmuSendDTO request,
                                                     LocalDateTime sendTime) {
        DanmuPersistMessage message = new DanmuPersistMessage();
        message.setId(danmuId);
        message.setUserId(userId);
        message.setVideoId(request.getVideoId());
        message.setContent(request.getContent().trim());
        message.setColor(normalizeColor(request.getColor()));
        message.setPosition(request.getPosition() == null ? 0 : request.getPosition());
        message.setFontSize(request.getFontSize() == null ? 16 : request.getFontSize());
        message.setVideoTime(request.getVideoTime());
        message.setStatus(0);
        message.setSendTime(sendTime);
        return message;
    }

    private DanmuBroadcastVO buildBroadcast(DanmuPersistMessage message) {
        UserBrief user = danmuMapper.selectUserBrief(message.getUserId());
        DanmuBroadcastVO result = new DanmuBroadcastVO();
        result.setId(message.getId());
        result.setVideoId(message.getVideoId());
        result.setUserId(message.getUserId());
        if (user != null) {
            result.setNickname(user.getNickname());
            result.setAvatar(user.getAvatar());
        }
        result.setContent(message.getContent());
        result.setColor(message.getColor());
        result.setPosition(message.getPosition());
        result.setFontSize(message.getFontSize());
        result.setVideoTime(message.getVideoTime());
        result.setSendTime(message.getSendTime());
        return result;
    }

    private DanmuListVO toListVO(Danmu danmu) {
        DanmuListVO result = new DanmuListVO();
        result.setId(danmu.getId());
        result.setUserId(danmu.getUserId());
        result.setContent(danmu.getContent());
        result.setColor(danmu.getColor());
        result.setPosition(danmu.getPosition());
        result.setFontSize(danmu.getFontSize());
        result.setVideoTime(danmu.getVideoTime());
        result.setSendTime(danmu.getSendTime());
        return result;
    }

    private DanmuSendVO buildSendResult(Long danmuId) {
        DanmuSendVO result = new DanmuSendVO();
        result.setAccepted(true);
        result.setDanmuId(danmuId);
        return result;
    }

    private String normalizeColor(String color) {
        return StringUtils.hasText(color) ? color.toUpperCase(Locale.ROOT) : "#FFFFFF";
    }

    private String normalizeRequestId(String requestId) {
        return StringUtils.hasText(requestId) ? requestId.trim() : null;
    }

    private String requestKey(Long userId, String requestId) {
        return DanmuConstant.REQUEST_KEY_PREFIX + userId + ":" + requestId;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
