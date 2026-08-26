package com.gary.bilibili.video.service;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.mapper.VideoMapper;
import org.springframework.stereotype.Service;

@Service
public class AdminAuthorizationService {
    private final VideoMapper videoMapper;

    public AdminAuthorizationService(VideoMapper videoMapper) { this.videoMapper = videoMapper; }

    public long requireAdmin() {
        long userId = StpUtil.getLoginIdAsLong();
        if (!"admin".equals(videoMapper.selectUserRole(userId))) throw new BusinessException("需要管理员权限");
        return userId;
    }
}
