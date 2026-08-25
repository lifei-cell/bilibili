package com.gary.bilibili.video.vo;

import lombok.Data;

@Data
public class UploadMergeVO {

    private String sourceUrl;
    private String fileMd5;
    private String transcodeTaskId;
    private String transcodeStatus;
}
