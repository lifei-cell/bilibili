package com.gary.bilibili.social.constant;

public final class SocialConstant {

    public static final int STATUS_INACTIVE = 0;
    public static final int STATUS_ACTIVE = 1;
    public static final int TARGET_VIDEO = 1;
    public static final int TARGET_COMMENT = 2;
    public static final long DEFAULT_FOLDER_ID = 0L;
    public static final String FOLLOWING_CACHE_KEY_PREFIX = "follow:following:";
    public static final String LIKE_CACHE_KEY_PREFIX = "like:";
    public static final String COMMENT_RATE_LIMIT_KEY_PREFIX = "comment:";
    public static final String STATS_CACHE_KEY_PREFIX = "video:stats:";
    public static final String STATS_PENDING_KEY = "video:stats:social:pending";
    public static final String STATS_SYNC_LOCK_KEY_PREFIX = "video:stats:social:sync:lock:";
    public static final String DETAIL_CACHE_KEY_PREFIX = "video:detail:";
    public static final String LIKE_COUNT_FIELD = "likeCount";
    public static final String COLLECT_COUNT_FIELD = "collectCount";
    public static final String COMMENT_COUNT_FIELD = "commentCount";
    public static final int COMMENT_RATE_LIMIT_COUNT = 30;
    public static final int COMMENT_RATE_LIMIT_WINDOW_SECONDS = 60;

    private SocialConstant() {
    }
}
