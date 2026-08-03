package com.gary.bilibili.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class VideoPublishDTO {

    @NotBlank
    @Size(max = 200)
    private String title;

    @Size(max = 65535)
    private String description;

    @Size(max = 500)
    private String coverUrl;

    @NotBlank
    @Size(max = 500)
    private String sourceUrl;

    @NotBlank
    @Pattern(regexp = "(?i)^[0-9a-f]{32}$")
    private String fileMd5;

    @NotNull
    @Min(1)
    private Long fileSize;

    @NotNull
    @Min(0)
    private Integer duration;

    @NotBlank
    @Size(max = 20)
    private String resolution;

    @NotNull
    @Min(1)
    private Long categoryId;

    @NotNull
    private List<@NotBlank @Size(max = 50) String> tags;
}
