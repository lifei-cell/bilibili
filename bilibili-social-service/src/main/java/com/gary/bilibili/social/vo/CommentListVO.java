package com.gary.bilibili.social.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommentListVO {

    private Long id;
    private Long userId;
    private String nickname;
    private String avatar;
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;
    private List<CommentReplyVO> replies;
}
