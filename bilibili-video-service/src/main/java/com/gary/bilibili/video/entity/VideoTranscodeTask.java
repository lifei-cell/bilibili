package com.gary.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_transcode_task")
public class VideoTranscodeTask {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskId;
    private Long userId;
    private String fileMd5;
    private String fileName;
    private Long fileSize;
    private String sourceUrl;
    private String sourceObjectName;
    private String outputUrl;
    private String coverUrl;
    private String variantsJson;
    private Integer status;
    private Integer retryCount;
    private Long claimGeneration;
    private String claimToken;
    private LocalDateTime leaseUntil;
    private String errorMessage;
    private LocalDateTime nextRetryTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
