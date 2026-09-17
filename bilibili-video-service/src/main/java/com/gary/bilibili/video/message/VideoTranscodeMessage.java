package com.gary.bilibili.video.message;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoTranscodeMessage {

    private String taskId;
    private Long claimGeneration;
    private String claimToken;
    private Long userId;
    private String fileMd5;
    private String fileName;
    private Long fileSize;
    private String sourceUrl;
    private String sourceObjectName;
    private LocalDateTime createTime;
}
