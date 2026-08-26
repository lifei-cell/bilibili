package com.gary.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.video.entity.DirectUploadSession;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

public interface DirectUploadSessionMapper extends BaseMapper<DirectUploadSession> {
    @Select("select * from direct_upload_session where upload_id = #{uploadId} limit 1")
    DirectUploadSession selectByUploadId(@Param("uploadId") String uploadId);

    @Update("update direct_upload_session set status = 1, update_time = now() "
            + "where upload_id = #{uploadId} and user_id = #{userId} and status = 0 and expire_time > now()")
    int markCompleted(@Param("uploadId") String uploadId, @Param("userId") long userId);
}
