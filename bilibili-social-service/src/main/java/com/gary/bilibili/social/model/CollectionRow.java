package com.gary.bilibili.social.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CollectionRow {

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
