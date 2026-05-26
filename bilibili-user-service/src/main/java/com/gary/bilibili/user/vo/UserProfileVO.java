package com.gary.bilibili.user.vo;

import lombok.Data;

@Data
public class UserProfileVO {

    private Long id;
    private String username;
    private String nickname;
    private String avatar;
    private String signature;
    private Long videoCount;
    private Long followerCount;
    private Long followingCount;
    private Boolean isFollowing;
}
