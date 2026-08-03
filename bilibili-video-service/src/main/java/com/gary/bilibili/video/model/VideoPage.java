package com.gary.bilibili.video.model;

import com.gary.bilibili.video.vo.VideoListVO;
import lombok.Data;

import java.util.List;

@Data
public class VideoPage {

    private List<VideoListVO> records;
    private Long total;
}
