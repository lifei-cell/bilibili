package com.gary.bilibili.video.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.mapper.VideoMapper;
import com.gary.bilibili.video.mapper.VideoTranscodeTaskMapper;
import com.gary.bilibili.video.model.MediaTranscodeResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Commits the transcode task and every matching video reference together. */
@Service
public class VideoTranscodeResultService {

    private final VideoTranscodeTaskMapper taskMapper;
    private final VideoMapper videoMapper;
    private final ObjectMapper objectMapper;

    public VideoTranscodeResultService(VideoTranscodeTaskMapper taskMapper,
                                       VideoMapper videoMapper,
                                       ObjectMapper objectMapper) {
        this.taskMapper = taskMapper;
        this.videoMapper = videoMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void publish(VideoTranscodeTask task, MediaTranscodeResult result) {
        String variantsJson = serialize(result);
        if (taskMapper.markSuccess(task.getTaskId(), result.masterUrl(),
                result.coverUrl(), variantsJson) != 1) {
            throw new IllegalStateException("Video transcode task was not found: " + task.getTaskId());
        }
        // Zero rows is valid when the user has not published a video yet.
        videoMapper.updateMediaByFileMd5(task.getFileMd5(), result.masterUrl(), result.coverUrl());
    }

    private String serialize(MediaTranscodeResult result) {
        try {
            return objectMapper.writeValueAsString(result.variants());
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Serialize media variants failed", exception);
        }
    }
}
