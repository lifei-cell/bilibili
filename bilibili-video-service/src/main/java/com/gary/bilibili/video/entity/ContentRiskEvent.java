package com.gary.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("content_risk_event")
public class ContentRiskEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String targetType;
    private Long targetId;
    private Long userId;
    private String scene;
    private String riskLevel;
    private Integer score;
    private String matchedRules;
    private String decision;
}
