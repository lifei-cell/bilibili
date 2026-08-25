package com.gary.bilibili.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "auth.session")
public class AuthSessionProperties {

    private long accessTokenTtlSeconds = 15 * 60;
    private long refreshTokenTtlSeconds = 30L * 24 * 60 * 60;
    private final Cookie cookie = new Cookie();

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = Math.max(60, accessTokenTtlSeconds);
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = Math.max(300, refreshTokenTtlSeconds);
    }

    public Cookie getCookie() {
        return cookie;
    }

    public static class Cookie {

        private String name = "bili_refresh";
        private String path = "/api/user";
        private boolean secure;
        private String sameSite = "Strict";

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getPath() {
            return path;
        }

        public void setPath(String path) {
            this.path = path;
        }

        public boolean isSecure() {
            return secure;
        }

        public void setSecure(boolean secure) {
            this.secure = secure;
        }

        public String getSameSite() {
            return sameSite;
        }

        public void setSameSite(String sameSite) {
            this.sameSite = sameSite;
        }
    }
}
