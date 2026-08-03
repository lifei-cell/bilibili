package com.gary.bilibili.video.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("video_stats")
public class VideoStats {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long videoId;
    private Long viewCount;
    private Long likeCount;
    private Long coinCount;
    private Long collectCount;
    private Long shareCount;
    private Long danmuCount;
    private Long commentCount;
    private LocalDateTime updateTime;
}
