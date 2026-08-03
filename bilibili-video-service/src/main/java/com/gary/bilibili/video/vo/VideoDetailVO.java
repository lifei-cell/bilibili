package com.gary.bilibili.video.vo;

import lombok.Data;

import java.util.List;

@Data
public class VideoDetailVO {

    private Long id;
    private String title;
    private String description;
    private String coverUrl;
    private Integer duration;
    private Long categoryId;
    private List<String> tags;
    private VideoAuthorVO author;
    private VideoStatsVO stats;
}
