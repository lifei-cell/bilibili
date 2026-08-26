package com.gary.bilibili.video.vo;

import lombok.Data;

@Data
public class DirectUploadInitVO {
    private String uploadId;
    private String uploadUrl;
    private Integer expiresIn;
    private String contentType;
}
