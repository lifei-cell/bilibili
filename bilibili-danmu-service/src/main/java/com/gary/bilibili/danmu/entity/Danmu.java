package com.gary.bilibili.danmu.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("danmu")
public class Danmu {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long videoId;
    private String content;
    private String color;
    private Integer position;
    private Integer fontSize;
    private Integer videoTime;
    private Integer status;
    private LocalDateTime sendTime;
}
