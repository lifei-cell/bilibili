package com.gary.bilibili.video.message;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class VideoViewMessage {

    private Long videoId;
    private String requestId;
    private LocalDateTime createTime;
}
