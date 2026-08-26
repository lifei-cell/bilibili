package com.gary.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video")
public class Video {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String description;
    private String coverUrl;
    private String sourceUrl;
    private String playUrl;
    private String fileMd5;
    private Long fileSize;
    private Integer duration;
    private String resolution;
    private Long categoryId;
    private String tags;
    private Integer status;
    private String auditRemark;
    private String riskLevel;
    private Long auditBy;
    private LocalDateTime auditTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
