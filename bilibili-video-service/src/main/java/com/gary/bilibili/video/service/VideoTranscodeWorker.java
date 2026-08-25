package com.gary.bilibili.video.service;

import com.gary.bilibili.video.entity.VideoTranscodeTask;

public interface VideoTranscodeWorker {

    String transcode(VideoTranscodeTask task);
}
