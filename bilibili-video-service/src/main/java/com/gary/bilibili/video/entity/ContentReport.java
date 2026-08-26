package com.gary.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_report")
public class ContentReport {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reporterId;
    private String targetType;
    private Long targetId;
    private String reasonCode;
    private String description;
    private String evidenceUrl;
    private Integer status;
    private Long handledBy;
    private String handleRemark;
    private LocalDateTime handledTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
