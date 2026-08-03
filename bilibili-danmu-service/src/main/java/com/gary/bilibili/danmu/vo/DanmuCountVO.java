package com.gary.bilibili.danmu.vo;

import lombok.Data;

import java.util.List;

@Data
public class DanmuCountVO {

    private Long total;
    private List<DanmuTimeCountVO> timeDistributed;
}
