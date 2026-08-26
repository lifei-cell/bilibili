package com.gary.bilibili.video.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminVideoRow {
    private Long id;
    private Long userId;
    private String authorName;
    private String title;
    private String coverUrl;
    private String playUrl;
    private Integer status;
    private String auditRemark;
    private String riskLevel;
    private LocalDateTime createTime;
}
