package com.gary.bilibili.video.service;

import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.model.MediaTranscodeResult;

public interface VideoTranscodeWorker {

    MediaTranscodeResult transcode(VideoTranscodeTask task);
}
