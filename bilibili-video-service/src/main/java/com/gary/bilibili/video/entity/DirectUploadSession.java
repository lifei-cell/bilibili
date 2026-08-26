package com.gary.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("direct_upload_session")
public class DirectUploadSession {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String uploadId;
    private Long userId;
    private String fileMd5;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String objectName;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
