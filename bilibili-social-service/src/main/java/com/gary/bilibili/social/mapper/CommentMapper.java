package com.gary.bilibili.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.social.entity.Comment;
import com.gary.bilibili.social.model.CommentRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface CommentMapper extends BaseMapper<Comment> {

    @Select("select count(*) from video where id = #{videoId} and status = 1 and deleted = 0")
    Long countPublishedVideo(@Param("videoId") Long videoId);

    @Select("select count(*) from sys_user where id = #{userId} and status = 0 and deleted = 0")
    Long countEnabledUser(@Param("userId") Long userId);

    @Select("""
            <script>
            select c.id, c.user_id, u.nickname, u.avatar, c.video_id, c.parent_id,
                   c.reply_to_id, c.content, c.like_count, c.create_time
            from comment c
            join sys_user u on u.id = c.user_id and u.deleted = 0
            where c.video_id = #{videoId} and c.parent_id = 0
              and c.status = 0 and c.deleted = 0
            <choose>
                <when test='sort == "hot"'>
                    order by c.like_count desc, c.create_time desc
                </when>
                <otherwise>
                    order by c.create_time desc
                </otherwise>
            </choose>
            limit #{offset}, #{size}
            </script>
            """)
    List<CommentRow> selectTopLevel(@Param("videoId") Long videoId,
                                    @Param("sort") String sort,
                                    @Param("offset") long offset,
                                    @Param("size") int size);

    @Select("""
            <script>
            select c.id, c.user_id, u.nickname, u.avatar, c.video_id, c.parent_id,
                   c.reply_to_id, ru.nickname as reply_to_nickname,
                   c.content, c.like_count, c.create_time
            from comment c
            join sys_user u on u.id = c.user_id and u.deleted = 0
            left join sys_user ru on ru.id = c.reply_to_id and ru.deleted = 0
            where c.parent_id in
            <foreach collection="parentIds" item="parentId" open="(" separator="," close=")">
                #{parentId}
            </foreach>
              and c.status = 0 and c.deleted = 0
            order by c.create_time asc, c.id asc
            </script>
            """)
    List<CommentRow> selectReplies(@Param("parentIds") List<Long> parentIds);

    @Select("select count(*) from comment where video_id = #{videoId} and parent_id = 0 and status = 0 and deleted = 0")
    Long countTopLevel(@Param("videoId") Long videoId);

    @Select("select user_id from video where id = #{videoId} and deleted = 0 limit 1")
    Long selectVideoAuthorId(@Param("videoId") Long videoId);

    @Select("select role from sys_user where id = #{userId} and status = 0 and deleted = 0 limit 1")
    String selectUserRole(@Param("userId") Long userId);

    @Select("select count(*) from comment where (id = #{commentId} or parent_id = #{commentId}) and status = 0 and deleted = 0")
    Long countCommentTree(@Param("commentId") Long commentId);

    @Update("""
            <script>
            update comment set deleted = 1
            where status = 0 and deleted = 0
              and (id = #{commentId}
              <if test="includeReplies">
                  or parent_id = #{commentId}
              </if>)
            </script>
            """)
    int markDeleted(@Param("commentId") Long commentId,
                    @Param("includeReplies") boolean includeReplies);
}
