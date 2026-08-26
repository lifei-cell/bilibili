package com.gary.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.video.entity.ContentReport;
import com.gary.bilibili.video.model.AdminReportRow;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface ContentReportMapper extends BaseMapper<ContentReport> {
    @Select("select count(*) from video where id = #{id} and deleted = 0")
    long videoExists(@Param("id") long id);
    @Select("select count(*) from comment where id = #{id} and deleted = 0")
    long commentExists(@Param("id") long id);
    @Select("select count(*) from danmu where id = #{id}")
    long danmuExists(@Param("id") long id);

    @Select("select r.*, u.nickname as reporter_name from content_report r "
            + "left join sys_user u on u.id = r.reporter_id "
            + "where (#{status} is null or r.status = #{status}) order by r.id desc limit #{offset}, #{size}")
    List<AdminReportRow> selectQueue(@Param("status") Integer status, @Param("offset") long offset,
                                          @Param("size") int size);

    @Select("select count(*) from content_report where (#{status} is null or status = #{status})")
    long countQueue(@Param("status") Integer status);

    @Update("update content_report set status = #{status}, handled_by = #{adminId}, handle_remark = #{remark}, "
            + "handled_time = now(), update_time = now() where id = #{id} and status in (0, 1)")
    int resolve(@Param("id") long id, @Param("status") int status,
                @Param("adminId") long adminId, @Param("remark") String remark);

    @Update("update comment set status = 1 where id = #{id} and deleted = 0")
    int blockComment(@Param("id") long id);

    @Update("update danmu set status = 1 where id = #{id}")
    int blockDanmu(@Param("id") long id);
}
