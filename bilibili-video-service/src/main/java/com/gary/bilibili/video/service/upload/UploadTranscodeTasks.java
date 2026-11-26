package com.gary.bilibili.video.service.upload;

import cn.dev33.satoken.stp.StpUtil;
import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.constant.UploadConstant;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.model.UploadTask;
import com.gary.bilibili.video.vo.VideoTranscodeStatusVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/** Durable upload-to-transcode handoff and authorized status projection. */
@Service
public class UploadTranscodeTasks {
    private final VideoTranscodeTaskMapper videoTranscodeTaskMapper;
    public UploadTranscodeTasks(VideoTranscodeTaskMapper videoTranscodeTaskMapper) { this.videoTranscodeTaskMapper = videoTranscodeTaskMapper; }
    public VideoTranscodeStatusVO getTranscodeStatus(String taskId) {
        Long userId = StpUtil.getLoginIdAsLong();
        VideoTranscodeTask task = videoTranscodeTaskMapper.selectByTaskId(taskId);
        if (task == null || !userId.equals(task.getUserId())) {
            throw new BusinessException("转码任务不存在");
        }

        VideoTranscodeStatusVO result = new VideoTranscodeStatusVO();
        result.setTaskId(task.getTaskId());
        result.setStatus(toTranscodeState(task.getStatus()));
        result.setRetryCount(task.getRetryCount() == null ? 0 : task.getRetryCount());
        result.setNextRetryTime(task.getNextRetryTime());
        result.setRenditionStatus(toRenditionState(task.getRenditionStatus()));
        result.setRenditionRetryCount(task.getRenditionRetryCount() == null ? 0 : task.getRenditionRetryCount());
        result.setRenditionNextRetryTime(task.getRenditionNextRetryTime());
        result.setRenditionErrorMessage(task.getRenditionErrorMessage());
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_SUCCESS).equals(task.getStatus())) {
            result.setOutputUrl(task.getOutputUrl());
            result.setCoverUrl(task.getCoverUrl());
        }
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_FAILED).equals(task.getStatus())) {
            result.setErrorMessage(StringUtils.hasText(task.getErrorMessage())
                    ? task.getErrorMessage() : "视频转码失败");
        }
        return result;
    }

    public void saveTranscodeTask(UploadTask task, String sourceUrl, String sourceObjectName) {
        VideoTranscodeTask existing = videoTranscodeTaskMapper.selectByTaskId(task.getUploadId());
        if (existing == null) {
            VideoTranscodeTask transcodeTask = new VideoTranscodeTask();
            transcodeTask.setTaskId(task.getUploadId());
            transcodeTask.setUserId(task.getUserId());
            transcodeTask.setFileMd5(task.getFileMd5());
            transcodeTask.setFileName(task.getFileName());
            transcodeTask.setFileSize(task.getFileSize());
            transcodeTask.setSourceUrl(sourceUrl);
            transcodeTask.setSourceObjectName(sourceObjectName);
            transcodeTask.setStatus(UploadConstant.TRANSCODE_STATUS_PENDING);
            transcodeTask.setRetryCount(0);
            transcodeTask.setRenditionStatus(0);
            transcodeTask.setRenditionRetryCount(0);
            videoTranscodeTaskMapper.insert(transcodeTask);
            return;
        }
        if (!Integer.valueOf(UploadConstant.TRANSCODE_STATUS_SUCCESS).equals(existing.getStatus())) {
            videoTranscodeTaskMapper.resetPending(
                    task.getUploadId(), sourceUrl, sourceObjectName,
                    task.getFileName(), task.getFileSize());
        }
    }

    private String toTranscodeState(Integer status) {
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_SUCCESS).equals(status)) {
            return UploadConstant.TRANSCODE_STATE_COMPLETED;
        }
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_FAILED).equals(status)) {
            return UploadConstant.TRANSCODE_STATE_FAILED;
        }
        if (Integer.valueOf(UploadConstant.TRANSCODE_STATUS_PROCESSING).equals(status)
                || Integer.valueOf(UploadConstant.TRANSCODE_STATUS_DISPATCHED).equals(status)) {
            return UploadConstant.TRANSCODE_STATE_PROCESSING;
        }
        return UploadConstant.TRANSCODE_STATE_WAITING;
    }

    private String toRenditionState(Integer status) {
        if (status == null || status == 0) return "WAITING";
        if (status == 2) return "COMPLETED";
        if (status == 3) return "RETRY_PENDING";
        if (status == 4) return "FAILED";
        return "PROCESSING";
    }

}
