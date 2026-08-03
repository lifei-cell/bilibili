package com.gary.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.video.entity.VideoStats;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface VideoStatsMapper extends BaseMapper<VideoStats> {

    @Update("update video_stats set view_count = view_count + #{count} where video_id = #{videoId}")
    int incrementViewCount(@Param("videoId") Long videoId, @Param("count") long count);
}
