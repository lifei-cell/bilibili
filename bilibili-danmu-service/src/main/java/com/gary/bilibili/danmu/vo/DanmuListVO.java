package com.gary.bilibili.danmu.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class DanmuListVO {

    private Long id;
    private Long userId;
    private String content;
    private String color;
    private Integer position;
    private Integer fontSize;
    private Integer videoTime;
    private LocalDateTime sendTime;
}
