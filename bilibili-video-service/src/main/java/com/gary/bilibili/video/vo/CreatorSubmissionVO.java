package com.gary.bilibili.video.vo;

import java.time.LocalDateTime;
import java.util.List;

public record CreatorSubmissionVO(
        long id, String title, String description, String coverUrl, List<String> tags,
        int status, String auditRemark, LocalDateTime updateTime) {
}
