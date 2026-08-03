package com.gary.bilibili.video.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class UploadCheckVO {

    private Boolean instant;
    private Long videoId;
    private String sourceUrl;
    private String uploadId;
    private List<Integer> uploadedChunks = new ArrayList<>();
}
