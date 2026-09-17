package com.gary.bilibili.canal.constant;

public final class CanalConstant {

    public static final String CACHE_SYNC_TOPIC = "cache-sync";
    public static final String CACHE_SYNC_CONSUMER_GROUP = "bilibili-canal-cache-sync";
    public static final String VIDEO_INDEX = "video_search";
    public static final String LEGACY_VIDEO_INDEX = "video_index";
    public static final String VIDEO_INDEX_PREFIX = "video_index_v";
    public static final String VIDEO_TABLE = "video";
    public static final String VIDEO_STATS_TABLE = "video_stats";
    public static final String USER_TABLE = "sys_user";
    public static final String FOLLOW_TABLE = "follow";
    public static final String LIKE_TABLE = "user_like";
    public static final String EVENT_DELETE = "DELETE";
    public static final String VIDEO_DETAIL_CACHE_KEY_PREFIX = "video:detail:";
    public static final String USER_INFO_CACHE_KEY_PREFIX = "user:info:";
    public static final String FOLLOWING_CACHE_KEY_PREFIX = "follow:following:";
    public static final String LIKE_CACHE_KEY_PREFIX = "like:";
    public static final String VIDEO_BLOOM_FILTER_KEY = "video:bloom:id";
    public static final int VIDEO_STATUS_PUBLISHED = 1;

    private CanalConstant() {
    }
}
