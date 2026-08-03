package com.gary.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.video.entity.Video;
import com.gary.bilibili.video.model.VideoDetailRow;
import com.gary.bilibili.video.model.VideoListRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface VideoMapper extends BaseMapper<Video> {

    @Select("""
            select v.id, v.title, v.description, v.cover_url, v.duration, v.category_id, v.tags,
                   u.id as author_id, u.nickname as author_nickname, u.avatar as author_avatar,
                   coalesce(s.view_count, 0) as view_count,
                   coalesce(s.like_count, 0) as like_count,
                   coalesce(s.collect_count, 0) as collect_count,
                   coalesce(s.danmu_count, 0) as danmu_count,
                   coalesce(s.comment_count, 0) as comment_count
            from video v
            join sys_user u on u.id = v.user_id and u.deleted = 0
            left join video_stats s on s.video_id = v.id
            where v.id = #{videoId} and v.status = 1 and v.deleted = 0
            limit 1
            """)
    VideoDetailRow selectPublishedDetail(@Param("videoId") Long videoId);

    @Select("""
            <script>
            select v.id, v.title, v.cover_url, v.duration, u.nickname as author_name,
                   coalesce(s.view_count, 0) as view_count,
                   coalesce(s.danmu_count, 0) as danmu_count, v.create_time
            from video v
            join sys_user u on u.id = v.user_id and u.deleted = 0
            left join video_stats s on s.video_id = v.id
            where v.status = 1 and v.deleted = 0
            <if test="categoryId != null">
                and v.category_id = #{categoryId}
            </if>
            <if test="userId != null">
                and v.user_id = #{userId}
            </if>
            <choose>
                <when test='sort == "hot"'>
                    order by coalesce(s.view_count, 0) desc, v.create_time desc
                </when>
                <when test='sort == "new"'>
                    order by v.create_time desc
                </when>
                <otherwise>
                    order by v.id desc
                </otherwise>
            </choose>
            limit #{offset}, #{size}
            </script>
            """)
    List<VideoListRow> selectPublishedList(@Param("categoryId") Long categoryId,
                                           @Param("userId") Long userId,
                                           @Param("sort") String sort,
                                           @Param("offset") long offset,
                                           @Param("size") int size);

    @Select("""
            <script>
            select count(*) from video
            where status = 1 and deleted = 0
            <if test="categoryId != null">
                and category_id = #{categoryId}
            </if>
            <if test="userId != null">
                and user_id = #{userId}
            </if>
            </script>
            """)
    Long countPublishedList(@Param("categoryId") Long categoryId,
                            @Param("userId") Long userId);

    @Select("select id from video where status = 1 and deleted = 0")
    List<Long> selectPublishedIds();

    @Select("select role from sys_user where id = #{userId} and status = 0 and deleted = 0 limit 1")
    String selectUserRole(@Param("userId") Long userId);
}
