package com.gary.bilibili.video.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface VideoTranscodeTaskMapper extends BaseMapper<VideoTranscodeTask> {

    @Select("select * from video_transcode_task where task_id = #{taskId} limit 1")
    VideoTranscodeTask selectByTaskId(@Param("taskId") String taskId);

    @Select("select * from video_transcode_task "
            + "where (status = 0 and (next_retry_time is null or next_retry_time <= now())) "
            + "or (status in (1, 2) and update_time < date_sub(now(), interval #{reclaimAfterSeconds} second)) "
            + "order by update_time asc limit #{limit}")
    List<VideoTranscodeTask> selectDispatchable(@Param("limit") int limit,
                                                @Param("reclaimAfterSeconds") int reclaimAfterSeconds);

    @Select("select * from video_transcode_task "
            + "where file_md5 = #{fileMd5} and status = 3 limit 1")
    VideoTranscodeTask selectSuccessByFileMd5(@Param("fileMd5") String fileMd5);

    @Update("update video_transcode_task set "
            + "status = case when retry_count + 1 >= #{maxRetries} then 4 else 0 end, "
            + "retry_count = retry_count + 1, "
            + "next_retry_time = case when retry_count + 1 >= #{maxRetries} then null "
            + "else date_add(now(), interval #{retryDelaySeconds} second) end, "
            + "error_message = #{errorMessage}, update_time = now() "
            + "where task_id = #{taskId} and status <> 3")
    int markPendingAfterFailure(@Param("taskId") String taskId,
                                @Param("errorMessage") String errorMessage,
                                @Param("maxRetries") int maxRetries,
                                @Param("retryDelaySeconds") int retryDelaySeconds);

    @Update("update video_transcode_task set status = 1, next_retry_time = null, update_time = now() "
            + "where task_id = #{taskId} and "
            + "(status = 0 or (status in (1, 2) "
            + "and update_time < date_sub(now(), interval #{reclaimAfterSeconds} second)))")
    int markDispatched(@Param("taskId") String taskId,
                       @Param("reclaimAfterSeconds") int reclaimAfterSeconds);

    @Update("update video_transcode_task set status = 2, update_time = now() "
            + "where task_id = #{taskId} and status in (1, 2)")
    int markProcessing(@Param("taskId") String taskId);

    @Update("update video_transcode_task set status = 3, output_url = #{outputUrl}, "
            + "error_message = null, update_time = now() where task_id = #{taskId}")
    int markSuccess(@Param("taskId") String taskId,
                    @Param("outputUrl") String outputUrl);

    @Update("update video_transcode_task set source_url = #{sourceUrl}, source_object_name = #{sourceObjectName}, "
            + "file_name = #{fileName}, file_size = #{fileSize}, status = 0, retry_count = 0, "
            + "output_url = null, error_message = null, next_retry_time = null, update_time = now() "
            + "where task_id = #{taskId} and status <> 3")
    int resetPending(@Param("taskId") String taskId,
                     @Param("sourceUrl") String sourceUrl,
                     @Param("sourceObjectName") String sourceObjectName,
                     @Param("fileName") String fileName,
                     @Param("fileSize") Long fileSize);
}
