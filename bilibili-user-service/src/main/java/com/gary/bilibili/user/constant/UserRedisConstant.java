package com.gary.bilibili.user.constant;

public final class UserRedisConstant {

    public static final String LOGIN_CODE_PREFIX = "login:code:";
    public static final String USER_INFO_PREFIX = "user:info:";
    public static final long LOGIN_CODE_TTL_MINUTES = 30L;

    private UserRedisConstant() {
    }
}
