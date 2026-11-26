package com.gary.bilibili.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VideoResubmitDTO(
        @NotBlank @Size(max = 200) String title,
        @NotNull @Size(max = 65535) String description,
        @NotNull List<@NotBlank @Size(max = 50) String> tags) {
}
