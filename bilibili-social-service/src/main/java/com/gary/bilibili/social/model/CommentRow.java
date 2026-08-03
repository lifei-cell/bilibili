package com.gary.bilibili.social.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentRow {

    private Long id;
    private Long userId;
    private String nickname;
    private String avatar;
    private Long videoId;
    private Long parentId;
    private Long replyToId;
    private String replyToNickname;
    private String content;
    private Integer likeCount;
    private LocalDateTime createTime;
}
