package com.gary.bilibili.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class DirectUploadCompleteDTO {
    @NotBlank @Pattern(regexp = "^direct_[a-f0-9]{32}$")
    private String uploadId;
}
