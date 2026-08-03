package com.gary.bilibili.social.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectionListVO {

    private Long id;
    private Long videoId;
    private Long folderId;
    private String title;
    private String coverUrl;
    private Integer duration;
    private String authorName;
    private Long viewCount;
    private LocalDateTime createTime;
}
