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
    public static final int COMMENT_RATE_LIMIT_COUNT = 30;
    public static final int COMMENT_RATE_LIMIT_WINDOW_SECONDS = 60;

    private SocialConstant() {
    }
}
