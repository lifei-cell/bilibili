package com.gary.bilibili.user.service;

import com.gary.bilibili.user.config.AuthSessionProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class RefreshCookieService {

    private final AuthSessionProperties properties;

    public RefreshCookieService(AuthSessionProperties properties) {
        this.properties = properties;
    }

    public String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String cookieName = properties.getCookie().getName();
        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    public void write(HttpServletResponse response,
                      RefreshSessionService.IssuedRefreshToken token) {
        response.addHeader(HttpHeaders.SET_COOKIE,
                cookie(token.value(), Duration.ofSeconds(token.maxAgeSeconds())).toString());
    }

    public void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        AuthSessionProperties.Cookie config = properties.getCookie();
        return ResponseCookie.from(config.getName(), value)
                .httpOnly(true)
                .secure(config.isSecure())
                .sameSite(config.getSameSite())
                .path(config.getPath())
                .maxAge(maxAge)
                .build();
    }
}
