package com.gary.bilibili.video.vo;

import lombok.Data;

import java.util.List;

@Data
public class VideoPlayVO {

    private Long videoId;
    private String defaultQuality;
    private List<VideoQualityVO> qualities;
    private String playToken;
}
