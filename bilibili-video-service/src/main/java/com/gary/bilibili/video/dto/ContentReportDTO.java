package com.gary.bilibili.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ContentReportDTO {
    @NotBlank @Pattern(regexp = "VIDEO|COMMENT|DANMU")
    private String targetType;
    @NotNull @Min(1)
    private Long targetId;
    @NotBlank @Pattern(regexp = "SPAM|PORN|VIOLENCE|ABUSE|COPYRIGHT|OTHER")
    private String reasonCode;
    @Size(max = 500)
    private String description;
    @Size(max = 500)
    private String evidenceUrl;
}
