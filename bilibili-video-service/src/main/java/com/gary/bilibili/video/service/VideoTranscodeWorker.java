package com.gary.bilibili.video.service;

import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.model.MediaTranscodeResult;

import java.util.function.Consumer;

public interface VideoTranscodeWorker {

    default MediaTranscodeResult transcode(VideoTranscodeTask task) {
        return transcode(task, ignored -> { }, () -> { });
    }

    MediaTranscodeResult transcode(VideoTranscodeTask task,
                                  Consumer<MediaTranscodeResult> playableListener,
                                  Runnable assertLease);
}
