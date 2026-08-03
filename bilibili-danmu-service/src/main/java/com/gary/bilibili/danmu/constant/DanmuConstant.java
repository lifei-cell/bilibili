package com.gary.bilibili.danmu.constant;

public final class DanmuConstant {

    public static final String PERSIST_TOPIC = "danmu-persist";
    public static final String RATE_LIMIT_KEY_PREFIX = "danmu:";
    public static final String REQUEST_KEY_PREFIX = "danmu:request:";
    public static final String STATS_CACHE_KEY_PREFIX = "video:stats:";
    public static final String STATS_PENDING_KEY = "video:stats:danmu:pending";
    public static final String STATS_SYNC_LOCK_KEY_PREFIX = "video:stats:danmu:sync:lock:";
    public static final String DETAIL_CACHE_KEY_PREFIX = "video:detail:";
    public static final String DANMU_COUNT_FIELD = "danmuCount";
    public static final int REQUEST_EXPIRE_SECONDS = 60;
    public static final int RATE_LIMIT_COUNT = 20;
    public static final int RATE_LIMIT_WINDOW_SECONDS = 60;

    private DanmuConstant() {
    }
}
