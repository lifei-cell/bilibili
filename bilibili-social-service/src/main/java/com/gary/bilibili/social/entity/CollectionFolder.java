package com.gary.bilibili.social.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("collection_folder")
public class CollectionFolder {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String name;
    private Integer isPublic;
    private String description;
    private String coverUrl;
    private Integer videoCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    @TableLogic
    private Integer deleted;
}
