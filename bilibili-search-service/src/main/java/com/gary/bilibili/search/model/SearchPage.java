package com.gary.bilibili.search.model;

import com.gary.bilibili.search.vo.VideoSearchVO;
import lombok.Data;

import java.util.List;

@Data
public class SearchPage {

    private List<VideoSearchVO> records;
    private Long total;
}
