package com.gary.bilibili.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("follow")
public class Follow {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long followerId;
    private Long followedId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
