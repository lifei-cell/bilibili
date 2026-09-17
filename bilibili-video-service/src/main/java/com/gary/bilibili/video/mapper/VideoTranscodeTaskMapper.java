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
            + "or (status in (1, 2) and (lease_until is null or lease_until <= now(6))) "
            + "order by update_time asc limit #{limit}")
    List<VideoTranscodeTask> selectDispatchable(@Param("limit") int limit);

    @Select("select * from video_transcode_task "
            + "where file_md5 = #{fileMd5} and status = 3 limit 1")
    VideoTranscodeTask selectSuccessByFileMd5(@Param("fileMd5") String fileMd5);

    @Update("update video_transcode_task set "
            + "status = case when retry_count + 1 >= #{maxRetries} then 4 else 0 end, "
            + "retry_count = retry_count + 1, "
            + "next_retry_time = case when retry_count + 1 >= #{maxRetries} then null "
            + "else timestampadd(SECOND, #{retryDelaySeconds}, now(6)) end, "
            + "error_message = #{errorMessage}, claim_token = null, lease_until = null, update_time = now() "
            + "where task_id = #{taskId} and status = #{expectedStatus} "
            + "and claim_generation = #{generation} and claim_token = #{token} and lease_until > now(6)")
    int markPendingAfterFailure(@Param("taskId") String taskId,
                                @Param("generation") long generation,
                                @Param("token") String token,
                                @Param("expectedStatus") int expectedStatus,
                                @Param("errorMessage") String errorMessage,
                                @Param("maxRetries") int maxRetries,
                                @Param("retryDelaySeconds") int retryDelaySeconds);

    @Update("update video_transcode_task set status = 1, claim_generation = claim_generation + 1, "
            + "claim_token = #{token}, lease_until = timestampadd(SECOND, #{leaseSeconds}, now(6)), "
            + "next_retry_time = null, update_time = now() "
            + "where task_id = #{taskId} and claim_generation = #{expectedGeneration} and "
            + "((status = 0 and (next_retry_time is null or next_retry_time <= now())) "
            + "or (status in (1, 2) and (lease_until is null or lease_until <= now(6))))")
    int markDispatched(@Param("taskId") String taskId,
                       @Param("expectedGeneration") long expectedGeneration,
                       @Param("token") String token,
                       @Param("leaseSeconds") int leaseSeconds);

    @Update("update video_transcode_task set status = 2, update_time = now() "
            + "where task_id = #{taskId} and status = 1 and claim_generation = #{generation} "
            + "and claim_token = #{token} and lease_until > now(6)")
    int markProcessing(@Param("taskId") String taskId,
                       @Param("generation") long generation,
                       @Param("token") String token);

    @Update("update video_transcode_task set lease_until = timestampadd(SECOND, #{leaseSeconds}, now(6)), "
            + "update_time = now() where task_id = #{taskId} and status in (2, 3) "
            + "and claim_generation = #{generation} and claim_token = #{token} and lease_until > now(6)")
    int renewLease(@Param("taskId") String taskId,
                   @Param("generation") long generation,
                   @Param("token") String token,
                   @Param("leaseSeconds") int leaseSeconds);

    @Update("update video_transcode_task set status = 3, output_url = #{outputUrl}, "
            + "cover_url = #{coverUrl}, variants_json = #{variantsJson}, "
            + "claim_token = case when #{finalResult} then null else claim_token end, "
            + "lease_until = case when #{finalResult} then null else lease_until end, "
            + "error_message = null, update_time = now() where task_id = #{taskId} "
            + "and status in (2, 3) and claim_generation = #{generation} "
            + "and claim_token = #{token} and lease_until > now(6)")
    int markSuccess(@Param("taskId") String taskId,
                    @Param("generation") long generation,
                    @Param("token") String token,
                    @Param("outputUrl") String outputUrl,
                    @Param("coverUrl") String coverUrl,
                    @Param("variantsJson") String variantsJson,
                    @Param("finalResult") boolean finalResult);

    @Update("update video_transcode_task set error_message = #{errorMessage}, "
            + "claim_token = null, lease_until = null, update_time = now() "
            + "where task_id = #{taskId} and status = 3 and claim_generation = #{generation} "
            + "and claim_token = #{token} and lease_until > now(6)")
    int markDegradedSuccess(@Param("taskId") String taskId,
                            @Param("generation") long generation,
                            @Param("token") String token,
                            @Param("errorMessage") String errorMessage);

    @Update("update video_transcode_task set source_url = #{sourceUrl}, source_object_name = #{sourceObjectName}, "
            + "file_name = #{fileName}, file_size = #{fileSize}, status = 0, retry_count = 0, "
            + "output_url = null, cover_url = null, variants_json = null, "
            + "error_message = null, next_retry_time = null, claim_token = null, "
            + "claim_generation = claim_generation + 1, lease_until = null, update_time = now() "
            + "where task_id = #{taskId} and status <> 3")
    int resetPending(@Param("taskId") String taskId,
                     @Param("sourceUrl") String sourceUrl,
                     @Param("sourceObjectName") String sourceObjectName,
                     @Param("fileName") String fileName,
                     @Param("fileSize") Long fileSize);
}
