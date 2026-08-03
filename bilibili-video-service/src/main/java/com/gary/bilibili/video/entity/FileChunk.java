package com.gary.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("file_chunk")
public class FileChunk {

    @TableId(type = IdType.AUTO)
    private Long id;
    private String uploadId;
    private Long userId;
    private String fileMd5;
    private String chunkMd5;
    private Integer chunkIndex;
    private Long chunkSize;
    private Integer totalChunks;
    private String fileName;
    private String objectName;
    private Integer status;
    private LocalDateTime expireTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
