package com.gary.bilibili.social.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CommentCreateDTO {

    @NotNull
    @Min(1)
    private Long videoId;

    private String content;

    @Min(0)
    private Long parentId;

    @Min(0)
    private Long replyToId;
}
