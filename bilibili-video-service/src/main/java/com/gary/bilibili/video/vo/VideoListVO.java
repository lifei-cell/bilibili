package com.gary.bilibili.video.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoListVO {

    private Long id;
    private String title;
    private String coverUrl;
    private Integer duration;
    private String authorName;
    private Long viewCount;
    private Long danmuCount;
    private LocalDateTime createTime;
}
