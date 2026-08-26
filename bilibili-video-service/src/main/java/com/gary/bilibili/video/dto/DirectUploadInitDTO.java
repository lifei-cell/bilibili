package com.gary.bilibili.video.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DirectUploadInitDTO {
    @NotBlank @Size(max = 255)
    private String fileName;
    @NotBlank @Pattern(regexp = "^video/[a-zA-Z0-9.+-]+$")
    private String contentType;
    @NotNull @Min(1) @Max(5368709120L)
    private Long fileSize;
    @NotBlank @Pattern(regexp = "(?i)^[0-9a-f]{32}$")
    private String fileMd5;
}
