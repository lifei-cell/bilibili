package com.gary.bilibili.video.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class VideoUpdateDTO {

    @Size(max = 200)
    private String title;

    @Size(max = 65535)
    private String description;

    @Size(max = 500)
    private String coverUrl;

    @Min(1)
    private Long categoryId;

    private List<@NotBlank @Size(max = 50) String> tags;
}
