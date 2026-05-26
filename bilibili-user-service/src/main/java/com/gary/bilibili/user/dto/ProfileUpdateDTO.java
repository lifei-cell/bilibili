package com.gary.bilibili.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

@Data
public class ProfileUpdateDTO {

    @Size(max = 50, message = "昵称长度不能超过 50")
    private String nickname;

    @Size(max = 500, message = "头像地址长度不能超过 500")
    private String avatar;

    @Min(value = 0, message = "性别参数错误")
    @Max(value = 2, message = "性别参数错误")
    private Integer gender;

    private LocalDate birthday;

    @Size(max = 200, message = "个性签名长度不能超过 200")
    private String signature;
}
