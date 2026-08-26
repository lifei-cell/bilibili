package com.gary.bilibili.video.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AuditActionDTO {
    @NotBlank @Pattern(regexp = "APPROVE|REJECT|OFFLINE|RESTORE")
    private String action;
    @Size(max = 500)
    private String remark;
}
