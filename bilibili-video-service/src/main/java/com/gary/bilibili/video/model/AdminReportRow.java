package com.gary.bilibili.video.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminReportRow {
    private Long id;
    private Long reporterId;
    private String reporterName;
    private String targetType;
    private Long targetId;
    private String reasonCode;
    private String description;
    private String evidenceUrl;
    private Integer status;
    private LocalDateTime createTime;
}
