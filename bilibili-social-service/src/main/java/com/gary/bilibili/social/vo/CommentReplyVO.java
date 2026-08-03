package com.gary.bilibili.social.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentReplyVO {

    private Long id;
    private Long userId;
    private String nickname;
    private String avatar;
    private Long replyToId;
    private String replyToNickname;
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;
}
