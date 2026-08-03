package com.gary.bilibili.danmu.service;

import com.gary.bilibili.danmu.dto.DanmuSendDTO;
import com.gary.bilibili.danmu.model.DanmuPage;
import com.gary.bilibili.danmu.vo.DanmuCountVO;
import com.gary.bilibili.danmu.vo.DanmuSendVO;

public interface DanmuService {

    DanmuPage getList(Long videoId,
                      Integer startTime,
                      Integer endTime,
                      Integer page,
                      Integer size);

    DanmuSendVO send(DanmuSendDTO request);

    DanmuCountVO getCount(Long videoId);
}
