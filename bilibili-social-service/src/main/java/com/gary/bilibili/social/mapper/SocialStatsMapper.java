package com.gary.bilibili.social.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface SocialStatsMapper {

    @Update("update video_stats set like_count = greatest(like_count + #{delta}, 0) where video_id = #{videoId}")
    int incrementLikeCount(@Param("videoId") Long videoId, @Param("delta") long delta);

    @Update("update video_stats set collect_count = greatest(collect_count + #{delta}, 0) where video_id = #{videoId}")
    int incrementCollectCount(@Param("videoId") Long videoId, @Param("delta") long delta);

    @Update("update video_stats set comment_count = greatest(comment_count + #{delta}, 0) where video_id = #{videoId}")
    int incrementCommentCount(@Param("videoId") Long videoId, @Param("delta") long delta);
}
