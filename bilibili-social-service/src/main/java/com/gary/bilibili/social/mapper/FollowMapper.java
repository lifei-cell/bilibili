package com.gary.bilibili.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.social.entity.Follow;
import com.gary.bilibili.social.model.UserRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface FollowMapper extends BaseMapper<Follow> {

    @Update("update follow set status = 1 where follower_id = #{followerId} and followed_id = #{followedId} and status = 0")
    int activate(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    @Insert("insert ignore into follow (follower_id, followed_id, status) values (#{followerId}, #{followedId}, 1)")
    int insertActive(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    @Update("update follow set status = 0 where follower_id = #{followerId} and followed_id = #{followedId} and status = 1")
    int deactivate(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    @Select("select count(*) from follow where follower_id = #{followerId} and followed_id = #{followedId} and status = 1")
    Long countActive(@Param("followerId") Long followerId, @Param("followedId") Long followedId);

    @Select("select count(*) from sys_user where id = #{userId} and status = 0 and deleted = 0")
    Long countEnabledUser(@Param("userId") Long userId);

    @Select("""
            select u.id, u.nickname, u.avatar, u.signature
            from follow f
            join sys_user u on u.id = f.followed_id and u.status = 0 and u.deleted = 0
            where f.follower_id = #{userId} and f.status = 1
            order by f.update_time desc, f.id desc
            limit #{offset}, #{size}
            """)
    List<UserRow> selectFollowing(@Param("userId") Long userId,
                                  @Param("offset") long offset,
                                  @Param("size") int size);

    @Select("select count(*) from follow where follower_id = #{userId} and status = 1")
    Long countFollowing(@Param("userId") Long userId);

    @Select("""
            select u.id, u.nickname, u.avatar, u.signature
            from follow f
            join sys_user u on u.id = f.follower_id and u.status = 0 and u.deleted = 0
            where f.followed_id = #{userId} and f.status = 1
            order by f.update_time desc, f.id desc
            limit #{offset}, #{size}
            """)
    List<UserRow> selectFollowers(@Param("userId") Long userId,
                                  @Param("offset") long offset,
                                  @Param("size") int size);

    @Select("select count(*) from follow where followed_id = #{userId} and status = 1")
    Long countFollowers(@Param("userId") Long userId);
}
