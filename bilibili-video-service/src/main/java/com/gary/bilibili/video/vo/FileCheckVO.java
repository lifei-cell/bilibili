package com.gary.bilibili.video.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class FileCheckVO {

    private Boolean exist;
    private Long videoId;
    private List<Integer> uploadedChunks = new ArrayList<>();
}
