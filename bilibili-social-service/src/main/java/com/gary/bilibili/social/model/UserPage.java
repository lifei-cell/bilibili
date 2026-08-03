package com.gary.bilibili.social.model;

import com.gary.bilibili.social.vo.UserBriefVO;
import lombok.Data;

import java.util.List;

@Data
public class UserPage {

    private List<UserBriefVO> records;
    private Long total;
}
