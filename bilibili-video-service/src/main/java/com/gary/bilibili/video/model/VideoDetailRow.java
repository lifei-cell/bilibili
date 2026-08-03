package com.gary.bilibili.video.model;

import lombok.Data;

@Data
public class VideoDetailRow {

    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private Integer duration;
    private Long categoryId;
    private String tags;
    private Long authorId;
    private String authorNickname;
    private String authorAvatar;
    private Long viewCount;
    private Long likeCount;
    private Long collectCount;
    private Long danmuCount;
    private Long commentCount;
}
