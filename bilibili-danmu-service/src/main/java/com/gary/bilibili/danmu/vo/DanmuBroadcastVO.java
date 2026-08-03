package com.gary.bilibili.danmu.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DanmuBroadcastVO {

    private Long id;
    private Long videoId;
    private Long userId;
    private String nickname;
    private String avatar;
    private String content;
    private String color;
    private Integer position;
    private Integer fontSize;
    private Integer videoTime;
    private LocalDateTime sendTime;
}
