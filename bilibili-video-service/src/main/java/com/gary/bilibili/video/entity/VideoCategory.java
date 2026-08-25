package com.gary.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("video_category")
public class VideoCategory {

    private Long id;
    private Long parentId;
    private String name;
    private Integer sort;
}
