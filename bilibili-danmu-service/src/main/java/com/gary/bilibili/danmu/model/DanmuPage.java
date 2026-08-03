package com.gary.bilibili.danmu.model;

import com.gary.bilibili.danmu.vo.DanmuListVO;
import lombok.Data;

import java.util.List;

@Data
public class DanmuPage {

    private List<DanmuListVO> records;
    private Long total;
}
