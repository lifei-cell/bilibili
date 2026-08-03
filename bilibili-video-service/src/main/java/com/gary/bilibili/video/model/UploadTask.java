package com.gary.bilibili.video.model;

import lombok.Data;

@Data
public class UploadTask {

    private String uploadId;
    private Long userId;
    private String fileMd5;
    private String fileName;
    private Long fileSize;
    private Integer totalChunks;
}
