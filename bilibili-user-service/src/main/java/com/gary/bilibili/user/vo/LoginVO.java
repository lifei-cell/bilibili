package com.gary.bilibili.user.vo;

public class LoginVO {

    private String token;
    private Long expiresIn;
    private UserInfoVO user;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getExpiresIn() {
        return expiresIn;
    }

    public void setExpiresIn(Long expiresIn) {
        this.expiresIn = expiresIn;
    }

    public UserInfoVO getUser() {
        return user;
    }

    public void setUser(UserInfoVO user) {
        this.user = user;
    }
}
