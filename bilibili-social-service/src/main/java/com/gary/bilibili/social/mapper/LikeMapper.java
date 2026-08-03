package com.gary.bilibili.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.social.entity.UserLike;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface LikeMapper extends BaseMapper<UserLike> {

    @Update("update user_like set status = 1 where user_id = #{userId} and target_type = #{targetType} and target_id = #{targetId} and status = 0")
    int activate(@Param("userId") Long userId,
                 @Param("targetType") Integer targetType,
                 @Param("targetId") Long targetId);

    @Insert("insert ignore into user_like (user_id, target_type, target_id, status) values (#{userId}, #{targetType}, #{targetId}, 1)")
    int insertActive(@Param("userId") Long userId,
                     @Param("targetType") Integer targetType,
                     @Param("targetId") Long targetId);

    @Update("update user_like set status = 0 where user_id = #{userId} and target_type = #{targetType} and target_id = #{targetId} and status = 1")
    int deactivate(@Param("userId") Long userId,
                   @Param("targetType") Integer targetType,
                   @Param("targetId") Long targetId);

    @Select("select count(*) from user_like where user_id = #{userId} and target_type = #{targetType} and target_id = #{targetId} and status = 1")
    Long countActive(@Param("userId") Long userId,
                     @Param("targetType") Integer targetType,
                     @Param("targetId") Long targetId);

    @Select("select count(*) from video where id = #{videoId} and status = 1 and deleted = 0")
    Long countPublishedVideo(@Param("videoId") Long videoId);

    @Select("select count(*) from comment where id = #{commentId} and status = 0 and deleted = 0")
    Long countVisibleComment(@Param("commentId") Long commentId);

    @Update("update comment set like_count = greatest(like_count + #{delta}, 0) where id = #{commentId} and status = 0 and deleted = 0")
    int incrementCommentLikeCount(@Param("commentId") Long commentId,
                                  @Param("delta") int delta);
}
