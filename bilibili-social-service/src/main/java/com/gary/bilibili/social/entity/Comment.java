package com.gary.bilibili.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("comment")
public class Comment {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    private Long parentId;
    private Long replyToId;
    private String content;
    private Integer likeCount;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
