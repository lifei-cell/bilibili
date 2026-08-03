package com.gary.bilibili.social.service;

import com.gary.bilibili.social.dto.LikeDTO;
import com.gary.bilibili.social.vo.LikeVO;

public interface LikeService {

    LikeVO like(LikeDTO request);

    LikeVO unlike(LikeDTO request);

    LikeVO getStatus(Integer targetType, Long targetId);
}
