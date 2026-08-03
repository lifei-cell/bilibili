package com.gary.bilibili.search.vo;

import lombok.Data;

import java.util.List;

@Data
public class VideoSearchVO {

    private Long id;
    private String title;
    private String description;
    private List<String> tags;
    private Long categoryId;
    private Long userId;
    private Long viewCount;
    private Long likeCount;
    private String createTime;
}
