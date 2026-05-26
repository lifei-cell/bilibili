package com.gary.bilibili.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("sys_user_auth")
public class SysUserAuth {

    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String identityType;
    private String identifier;
    private String credential;
    private String salt;
    private Integer verified;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
