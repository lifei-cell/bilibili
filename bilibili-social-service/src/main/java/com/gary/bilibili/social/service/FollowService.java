package com.gary.bilibili.social.service;

import com.gary.bilibili.social.model.UserPage;
import com.gary.bilibili.social.vo.FollowStatusVO;
import com.gary.bilibili.social.vo.FollowVO;

public interface FollowService {

    FollowVO follow(Long userId);

    FollowVO unfollow(Long userId);

    FollowStatusVO getStatus(Long userId);

    UserPage getFollowing(Long userId, Integer page, Integer size);

    UserPage getFollowers(Long userId, Integer page, Integer size);
}
