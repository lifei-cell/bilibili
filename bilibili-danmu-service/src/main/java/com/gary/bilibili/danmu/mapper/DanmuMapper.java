package com.gary.bilibili.danmu.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.danmu.entity.Danmu;
import com.gary.bilibili.danmu.message.DanmuPersistMessage;
import com.gary.bilibili.danmu.model.DanmuTimeCount;
import com.gary.bilibili.danmu.model.UserBrief;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface DanmuMapper extends BaseMapper<Danmu> {

    @Select("""
            <script>
            select id, user_id, content, color, position, font_size, video_time, send_time
            from danmu
            where video_id = #{videoId} and status = 0
            <if test="startTime != null">
                and video_time &gt;= #{startTime}
            </if>
            <if test="endTime != null">
                and video_time &lt;= #{endTime}
            </if>
            order by video_time asc, id asc
            limit #{offset}, #{size}
            </script>
            """)
    List<Danmu> selectDanmuList(@Param("videoId") Long videoId,
                                @Param("startTime") Integer startTime,
                                @Param("endTime") Integer endTime,
                                @Param("offset") long offset,
                                @Param("size") int size);

    @Select("""
            <script>
            select count(*) from danmu
            where video_id = #{videoId} and status = 0
            <if test="startTime != null">
                and video_time &gt;= #{startTime}
            </if>
            <if test="endTime != null">
                and video_time &lt;= #{endTime}
            </if>
            </script>
            """)
    Long countDanmuList(@Param("videoId") Long videoId,
                        @Param("startTime") Integer startTime,
                        @Param("endTime") Integer endTime);

    @Select("select count(*) from video where id = #{videoId} and status = 1 and deleted = 0")
    Long countPublishedVideo(@Param("videoId") Long videoId);

    @Select("select nickname, avatar from sys_user where id = #{userId} and status = 0 and deleted = 0 limit 1")
    UserBrief selectUserBrief(@Param("userId") Long userId);

    @Select("select count(*) from danmu where video_id = #{videoId} and status = 0")
    Long countByVideo(@Param("videoId") Long videoId);

    @Select("""
            select floor(video_time / 60) * 60 as time, count(*) as count
            from danmu
            where video_id = #{videoId} and status = 0
            group by floor(video_time / 60) * 60
            order by time asc
            """)
    List<DanmuTimeCount> selectTimeDistributed(@Param("videoId") Long videoId);

    @Insert("""
            <script>
            insert ignore into danmu
                (id, user_id, video_id, content, color, position, font_size, video_time, status, send_time)
            values
            <foreach collection="messages" item="item" separator=",">
                (#{item.id}, #{item.userId}, #{item.videoId}, #{item.content}, #{item.color},
                 #{item.position}, #{item.fontSize}, #{item.videoTime}, #{item.status}, #{item.sendTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("messages") List<DanmuPersistMessage> messages);

    @Update("update video_stats set danmu_count = danmu_count + #{count} where video_id = #{videoId}")
    int incrementDanmuCount(@Param("videoId") Long videoId, @Param("count") long count);
}
