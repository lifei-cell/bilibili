package com.gary.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("content_audit_log")
public class ContentAuditLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String targetType;
    private Long targetId;
    private String action;
    private Long operatorId;
    private Integer previousStatus;
    private Integer currentStatus;
    private String remark;
    private LocalDateTime createTime;
}
