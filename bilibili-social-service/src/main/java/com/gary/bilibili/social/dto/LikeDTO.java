package com.gary.bilibili.social.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LikeDTO {

    @NotNull
    @Min(1)
    @Max(2)
    private Integer targetType;

    @NotNull
    @Min(1)
    private Long targetId;
}
