package com.gary.bilibili.video.vo;

import lombok.Data;

@Data
public class VideoStatsVO {

    private Long viewCount;
    private Long likeCount;
    private Long collectCount;
    private Long danmuCount;
    private Long commentCount;
}
