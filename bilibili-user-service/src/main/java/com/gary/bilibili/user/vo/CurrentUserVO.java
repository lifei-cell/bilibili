package com.gary.bilibili.user.vo;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CurrentUserVO {

    private Long id;
    private String username;
    private String nickname;
    private String phone;
    private String avatar;
    private Integer gender;
    private LocalDate birthday;
    private String signature;
    private String role;
}
