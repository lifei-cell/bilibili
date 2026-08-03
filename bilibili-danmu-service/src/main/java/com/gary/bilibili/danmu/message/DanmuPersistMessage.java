package com.gary.bilibili.danmu.message;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DanmuPersistMessage {

    private Long id;
    private Long userId;
    private Long videoId;
    private String content;
    private String color;
    private Integer position;
    private Integer fontSize;
    private Integer videoTime;
    private Integer status;
    private LocalDateTime sendTime;
}
