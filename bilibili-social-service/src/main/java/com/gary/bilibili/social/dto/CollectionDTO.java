package com.gary.bilibili.social.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CollectionDTO {

    @NotNull
    @Min(1)
    private Long videoId;

    @Min(0)
    private Long folderId;
}
