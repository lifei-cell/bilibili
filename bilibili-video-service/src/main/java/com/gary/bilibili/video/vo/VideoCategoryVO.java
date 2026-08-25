package com.gary.bilibili.video.vo;

import lombok.Data;

@Data
public class VideoCategoryVO {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sort;
}
