package com.gary.bilibili.video.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UploadProgressVO {

    private String uploadId;
    private List<Integer> uploadedChunks = new ArrayList<>();
    private Integer totalChunks;
    private Integer percent;
}
