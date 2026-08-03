package com.gary.bilibili.video.constant;

public final class VideoConstant {

    public static final int STATUS_AUDITING = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_OFFLINE = 3;
    public static final int DETAIL_CACHE_TTL_MINUTES = 30;
    public static final String DETAIL_CACHE_KEY_PREFIX = "video:detail:";
    public static final String STATS_CACHE_KEY_PREFIX = "video:stats:";
    public static final String STATS_PENDING_KEY = "video:stats:pending";
    public static final String STATS_SYNC_LOCK_KEY_PREFIX = "video:stats:sync:lock:";
    public static final String VIEW_COUNT_FIELD = "viewCount";
    public static final String BLOOM_FILTER_KEY = "video:bloom:id";
    public static final String VIEW_TOPIC = "video-view";
    public static final String SORT_DEFAULT = "default";
    public static final String SORT_HOT = "hot";
    public static final String SORT_NEW = "new";

    private VideoConstant() {
    }
}
