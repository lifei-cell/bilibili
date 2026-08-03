package com.gary.bilibili.social.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.social.constant.SocialConstant;
import com.gary.bilibili.social.dto.LikeDTO;
import com.gary.bilibili.social.mapper.LikeMapper;
import com.gary.bilibili.social.service.LikeService;
import com.gary.bilibili.social.service.SocialStatsService;
import com.gary.bilibili.social.vo.LikeVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LikeServiceImpl implements LikeService {

    private static final Logger log = LoggerFactory.getLogger(LikeServiceImpl.class);

    private final LikeMapper likeMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final SocialStatsService socialStatsService;

    public LikeServiceImpl(LikeMapper likeMapper,
                           StringRedisTemplate stringRedisTemplate,
                           SocialStatsService socialStatsService) {
        this.likeMapper = likeMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.socialStatsService = socialStatsService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeVO like(LikeDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        assertTargetExists(request.getTargetType(), request.getTargetId());

        int changed = likeMapper.activate(userId, request.getTargetType(), request.getTargetId());
        if (changed == 0) {
            changed = likeMapper.insertActive(userId, request.getTargetType(), request.getTargetId());
        }
        if (changed > 0) {
            updateCount(request.getTargetType(), request.getTargetId(), 1);
        }
        cacheLike(userId, request.getTargetType(), request.getTargetId(), true);
        return buildResult(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LikeVO unlike(LikeDTO request) {
        Long userId = StpUtil.getLoginIdAsLong();
        int changed = likeMapper.deactivate(userId, request.getTargetType(), request.getTargetId());
        if (changed > 0) {
            updateCount(request.getTargetType(), request.getTargetId(), -1);
        }
        cacheLike(userId, request.getTargetType(), request.getTargetId(), false);
        return buildResult(false);
    }

    @Override
    public LikeVO getStatus(Integer targetType, Long targetId) {
        Long userId = StpUtil.getLoginIdAsLong();
        boolean liked = nullToZero(likeMapper.countActive(userId, targetType, targetId)) > 0;
        cacheLike(userId, targetType, targetId, liked);
        return buildResult(liked);
    }

    private void assertTargetExists(Integer targetType, Long targetId) {
        boolean exists;
        if (SocialConstant.TARGET_VIDEO == targetType) {
            exists = nullToZero(likeMapper.countPublishedVideo(targetId)) > 0;
        } else if (SocialConstant.TARGET_COMMENT == targetType) {
            exists = nullToZero(likeMapper.countVisibleComment(targetId)) > 0;
        } else {
            throw new BusinessException("请求参数错误");
        }
        if (!exists) {
            throw new BusinessException("资源不存在");
        }
    }

    private void updateCount(Integer targetType, Long targetId, int delta) {
        if (SocialConstant.TARGET_VIDEO == targetType) {
            socialStatsService.increment(targetId, SocialConstant.LIKE_COUNT_FIELD, delta);
        } else {
            likeMapper.incrementCommentLikeCount(targetId, delta);
        }
    }

    private void cacheLike(Long userId, Integer targetType, Long targetId, boolean liked) {
        try {
            String key = SocialConstant.LIKE_CACHE_KEY_PREFIX + targetType + ":" + targetId;
            if (liked) {
                stringRedisTemplate.opsForSet().add(key, userId.toString());
            } else {
                stringRedisTemplate.opsForSet().remove(key, userId.toString());
            }
        } catch (Exception exception) {
            log.warn("Update like cache failed, userId={}, targetType={}, targetId={}",
                    userId, targetType, targetId, exception);
        }
    }

    private LikeVO buildResult(boolean liked) {
        LikeVO result = new LikeVO();
        result.setLiked(liked);
        return result;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
