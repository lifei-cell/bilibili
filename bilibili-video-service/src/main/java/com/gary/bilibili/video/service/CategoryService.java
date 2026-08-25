package com.gary.bilibili.video.service;

import com.gary.bilibili.video.vo.VideoCategoryVO;

import java.util.List;

public interface CategoryService {

    List<VideoCategoryVO> listEnabled();
}
