package com.gary.bilibili.social.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.social.constant.SocialConstant;
import com.gary.bilibili.social.mapper.FollowMapper;
import com.gary.bilibili.social.model.UserPage;
import com.gary.bilibili.social.model.UserRow;
import com.gary.bilibili.social.service.FollowService;
import com.gary.bilibili.social.vo.FollowStatusVO;
import com.gary.bilibili.social.vo.FollowVO;
import com.gary.bilibili.social.vo.UserBriefVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
public class FollowServiceImpl implements FollowService {

    private static final Logger log = LoggerFactory.getLogger(FollowServiceImpl.class);

    private final FollowMapper followMapper;
    private final StringRedisTemplate stringRedisTemplate;

    public FollowServiceImpl(FollowMapper followMapper,
                             StringRedisTemplate stringRedisTemplate) {
        this.followMapper = followMapper;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowVO follow(Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        if (currentUserId.equals(userId)) {
            throw new BusinessException("不能关注自己");
        }
        assertUserExists(userId);

        if (followMapper.activate(currentUserId, userId) == 0) {
            followMapper.insertActive(currentUserId, userId);
        }
        cacheFollowing(currentUserId, userId, true);
        return buildResult(true);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FollowVO unfollow(Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        followMapper.deactivate(currentUserId, userId);
        cacheFollowing(currentUserId, userId, false);
        return buildResult(false);
    }

    @Override
    public FollowStatusVO getStatus(Long userId) {
        Long currentUserId = StpUtil.getLoginIdAsLong();
        boolean following = !currentUserId.equals(userId)
                && nullToZero(followMapper.countActive(currentUserId, userId)) > 0;
        cacheFollowing(currentUserId, userId, following);

        FollowStatusVO result = new FollowStatusVO();
        result.setIsFollowing(following);
        return result;
    }

    @Override
    public UserPage getFollowing(Long userId, Integer page, Integer size) {
        assertUserExists(userId);
        long offset = (long) (page - 1) * size;
        return buildPage(followMapper.selectFollowing(userId, offset, size),
                followMapper.countFollowing(userId));
    }

    @Override
    public UserPage getFollowers(Long userId, Integer page, Integer size) {
        assertUserExists(userId);
        long offset = (long) (page - 1) * size;
        return buildPage(followMapper.selectFollowers(userId, offset, size),
                followMapper.countFollowers(userId));
    }

    private UserPage buildPage(List<UserRow> rows, Long total) {
        List<UserBriefVO> records = new ArrayList<>(rows.size());
        for (UserRow row : rows) {
            UserBriefVO item = new UserBriefVO();
            item.setId(row.getId());
            item.setNickname(row.getNickname());
            item.setAvatar(row.getAvatar());
            item.setSignature(row.getSignature());
            records.add(item);
        }
        UserPage result = new UserPage();
        result.setRecords(records);
        result.setTotal(nullToZero(total));
        return result;
    }

    private void assertUserExists(Long userId) {
        if (nullToZero(followMapper.countEnabledUser(userId)) == 0) {
            throw new BusinessException("用户不存在");
        }
    }

    private void cacheFollowing(Long userId, Long followedId, boolean following) {
        try {
            String key = SocialConstant.FOLLOWING_CACHE_KEY_PREFIX + userId;
            if (following) {
                stringRedisTemplate.opsForSet().add(key, followedId.toString());
            } else {
                stringRedisTemplate.opsForSet().remove(key, followedId.toString());
            }
        } catch (Exception exception) {
            log.warn("Update following cache failed, userId={}, followedId={}",
                    userId, followedId, exception);
        }
    }

    private FollowVO buildResult(boolean followed) {
        FollowVO result = new FollowVO();
        result.setFollowed(followed);
        return result;
    }

    private long nullToZero(Long value) {
        return value == null ? 0L : value;
    }
}
