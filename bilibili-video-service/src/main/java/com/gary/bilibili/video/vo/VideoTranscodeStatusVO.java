package com.gary.bilibili.video.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * The client-facing state of a media-processing task. Source object details
 * deliberately stay server-side; callers only need to know whether they can
 * publish the work and, on completion, which playable asset was produced.
 */
@Data
public class VideoTranscodeStatusVO {

    private String taskId;
    private String status;
    private Integer retryCount;
    private String errorMessage;
    private String outputUrl;
    private String coverUrl;
    private LocalDateTime nextRetryTime;
}
