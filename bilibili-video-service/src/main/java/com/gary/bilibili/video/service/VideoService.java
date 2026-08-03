package com.gary.bilibili.video.service;

import com.gary.bilibili.video.dto.VideoPublishDTO;
import com.gary.bilibili.video.dto.VideoUpdateDTO;
import com.gary.bilibili.video.model.VideoPage;
import com.gary.bilibili.video.vo.VideoDetailVO;
import com.gary.bilibili.video.vo.VideoPlayVO;
import com.gary.bilibili.video.vo.VideoPublishVO;

public interface VideoService {

    VideoPublishVO publish(VideoPublishDTO request);

    VideoDetailVO getDetail(Long videoId);

    VideoPlayVO getPlayInfo(Long videoId);

    VideoPage getList(Integer page, Integer size, Long categoryId, String sort);

    VideoPage getUserVideos(Long userId, Integer page, Integer size, String sort);

    void update(Long videoId, VideoUpdateDTO request);

    void delete(Long videoId);
}
