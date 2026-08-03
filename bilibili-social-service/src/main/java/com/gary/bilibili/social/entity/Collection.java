package com.gary.bilibili.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("collection")
public class Collection {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long videoId;
    private Long folderId;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
