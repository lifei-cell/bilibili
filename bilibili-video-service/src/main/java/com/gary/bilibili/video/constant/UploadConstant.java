package com.gary.bilibili.video.constant;

public final class UploadConstant {

    public static final long CHUNK_SIZE = 5L * 1024 * 1024;
    public static final int TASK_EXPIRE_HOURS = 24;
    public static final String TASK_KEY_PREFIX = "upload:task:";
    public static final String FILE_TASK_KEY_PREFIX = "upload:file:";
    public static final String MERGE_LOCK_KEY_PREFIX = "upload:merge:";
    public static final String CHECK_RATE_LIMIT_KEY_PREFIX = "upload:check:";
    public static final String CHUNK_RATE_LIMIT_KEY_PREFIX = "upload:chunk:";
    public static final String TRANSCODE_DISPATCH_LOCK_KEY_PREFIX = "video:transcode:dispatch:";
    public static final String TRANSCODE_TOPIC = "video-transcode";
    public static final int TRANSCODE_STATUS_PENDING = 0;
    public static final int TRANSCODE_STATUS_DISPATCHED = 1;
    public static final int TRANSCODE_STATUS_PROCESSING = 2;
    public static final int TRANSCODE_STATUS_SUCCESS = 3;
    public static final int TRANSCODE_STATUS_FAILED = 4;

    public static final String TRANSCODE_STATE_WAITING = "waiting";
    public static final String TRANSCODE_STATE_PROCESSING = "processing";
    public static final String TRANSCODE_STATE_COMPLETED = "completed";
    public static final String TRANSCODE_STATE_FAILED = "failed";

    private UploadConstant() {
    }
}
