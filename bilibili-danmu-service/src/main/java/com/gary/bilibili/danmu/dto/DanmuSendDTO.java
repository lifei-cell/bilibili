package com.gary.bilibili.danmu.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class DanmuSendDTO {

    @NotNull
    @Min(1)
    private Long videoId;

    private String content;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$")
    private String color;

    @Min(0)
    @Max(2)
    private Integer position;

    @Min(1)
    @Max(100)
    private Integer fontSize;

    /**
     * Video position in whole seconds. The database and all API responses use
     * the same unit.
     */
    @NotNull
    @Min(0)
    private Integer videoTime;

    @Size(max = 64)
    private String requestId;
}
