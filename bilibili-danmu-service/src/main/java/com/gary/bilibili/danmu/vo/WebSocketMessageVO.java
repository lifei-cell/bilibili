package com.gary.bilibili.danmu.vo;

import lombok.Data;

@Data
public class WebSocketMessageVO {

    private String type;
    private Boolean success;
    private Object data;
    private String message;
    private Long timestamp;
}
