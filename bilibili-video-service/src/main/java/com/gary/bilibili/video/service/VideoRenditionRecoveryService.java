package com.gary.bilibili.video.service;

import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import org.springframework.stereotype.Service;

/** Reopens an exhausted adaptive rendition job without changing the playable result. */
@Service
public class VideoRenditionRecoveryService {

    private final VideoTranscodeTaskMapper taskMapper;

    public VideoRenditionRecoveryService(VideoTranscodeTaskMapper taskMapper) {
        this.taskMapper = taskMapper;
    }

    public void requeue(String taskId) {
        if (taskMapper.requeueFailedRenditions(taskId) != 1) {
            throw new BusinessException("高档位任务未处于可补偿失败状态");
        }
    }
}
