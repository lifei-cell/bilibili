package com.gary.bilibili.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.user.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("select count(*) from video where user_id = #{userId} and status = 1 and deleted = 0")
    Long countPublishedVideos(@Param("userId") Long userId);

    @Select("select count(*) from follow where followed_id = #{userId} and status = 1")
    Long countFollowers(@Param("userId") Long userId);

    @Select("select count(*) from follow where follower_id = #{userId} and status = 1")
    Long countFollowing(@Param("userId") Long userId);

    @Select("select count(*) from follow where follower_id = #{followerId} and followed_id = #{followedId} and status = 1")
    Long countFollowRelation(@Param("followerId") Long followerId, @Param("followedId") Long followedId);
}
