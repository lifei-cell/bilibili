package com.gary.bilibili.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FileCheckDTO {

    @NotBlank(message = "文件 MD5 不能为空")
    @Pattern(regexp = "(?i)^[a-f0-9]{32}$", message = "文件 MD5 格式错误")
    private String md5;

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名长度不能超过 255")
    private String fileName;
}
