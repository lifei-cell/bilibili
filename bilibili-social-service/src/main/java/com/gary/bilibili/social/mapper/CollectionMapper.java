package com.gary.bilibili.social.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.social.entity.Collection;
import com.gary.bilibili.social.model.CollectionRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface CollectionMapper extends BaseMapper<Collection> {

    @Update("update collection set status = 1 where user_id = #{userId} and video_id = #{videoId} and folder_id = #{folderId} and status = 0")
    int activate(@Param("userId") Long userId,
                 @Param("videoId") Long videoId,
                 @Param("folderId") Long folderId);

    @Insert("insert ignore into collection (user_id, video_id, folder_id, status) values (#{userId}, #{videoId}, #{folderId}, 1)")
    int insertActive(@Param("userId") Long userId,
                     @Param("videoId") Long videoId,
                     @Param("folderId") Long folderId);

    @Update("update collection set status = 0 where user_id = #{userId} and video_id = #{videoId} and folder_id = #{folderId} and status = 1")
    int deactivate(@Param("userId") Long userId,
                   @Param("videoId") Long videoId,
                   @Param("folderId") Long folderId);

    @Select("select count(*) from video where id = #{videoId} and status = 1 and deleted = 0")
    Long countPublishedVideo(@Param("videoId") Long videoId);

    @Select("""
            <script>
            select c.id, c.video_id, c.folder_id, v.title, v.cover_url, v.duration,
                   u.nickname as author_name, coalesce(s.view_count, 0) as view_count,
                   c.create_time
            from collection c
            join video v on v.id = c.video_id and v.status = 1 and v.deleted = 0
            join sys_user u on u.id = v.user_id and u.deleted = 0
            left join video_stats s on s.video_id = v.id
            where c.user_id = #{userId} and c.status = 1
            <if test="folderId != null">
                and c.folder_id = #{folderId}
            </if>
            order by c.update_time desc, c.id desc
            limit #{offset}, #{size}
            </script>
            """)
    List<CollectionRow> selectCollectionList(@Param("userId") Long userId,
                                             @Param("folderId") Long folderId,
                                             @Param("offset") long offset,
                                             @Param("size") int size);

    @Select("""
            <script>
            select count(*) from collection c
            join video v on v.id = c.video_id and v.status = 1 and v.deleted = 0
            where c.user_id = #{userId} and c.status = 1
            <if test="folderId != null">
                and c.folder_id = #{folderId}
            </if>
            </script>
            """)
    Long countCollectionList(@Param("userId") Long userId,
                             @Param("folderId") Long folderId);
}
