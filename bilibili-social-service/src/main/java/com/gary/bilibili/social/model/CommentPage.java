package com.gary.bilibili.social.model;

import com.gary.bilibili.social.vo.CommentListVO;
import lombok.Data;

import java.util.List;

@Data
public class CommentPage {

    private List<CommentListVO> records;
    private Long total;
}
