package com.gary.bilibili.user.exception;

public class AuthenticationExpiredException extends RuntimeException {

    public AuthenticationExpiredException() {
        super("登录已过期，请重新登录");
    }
}
