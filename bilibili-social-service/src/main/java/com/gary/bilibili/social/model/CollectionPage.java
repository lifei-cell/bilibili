package com.gary.bilibili.social.model;

import com.gary.bilibili.social.vo.CollectionListVO;
import lombok.Data;

import java.util.List;

@Data
public class CollectionPage {

    private List<CollectionListVO> records;
    private Long total;
}
