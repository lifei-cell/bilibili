package com.gary.bilibili.video.constant;

public final class UploadConstant {

    public static final long CHUNK_SIZE = 5L * 1024 * 1024;
    public static final int TASK_EXPIRE_HOURS = 24;
    public static final String TASK_KEY_PREFIX = "upload:task:";
    public static final String FILE_TASK_KEY_PREFIX = "upload:file:";
    public static final String MERGE_LOCK_KEY_PREFIX = "upload:merge:";
    public static final String CHECK_RATE_LIMIT_KEY_PREFIX = "upload:check:";
    public static final String CHUNK_RATE_LIMIT_KEY_PREFIX = "upload:chunk:";
    public static final String TRANSCODE_TOPIC = "video-transcode";

    private UploadConstant() {
    }
}
