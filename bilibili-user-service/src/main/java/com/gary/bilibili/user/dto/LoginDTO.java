package com.gary.bilibili.user.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginDTO {

    private String phone;
    private String code;
    private String username;

    @Size(max = 64, message = "密码长度不能超过 64")
    private String password;

    private String terminal = "web";
}
