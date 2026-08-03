package com.gary.bilibili.video.vo;

import lombok.Data;

@Data
public class UploadChunkVO {

    private String uploadId;
    private Integer chunkIndex;
    private Boolean uploaded;
}
