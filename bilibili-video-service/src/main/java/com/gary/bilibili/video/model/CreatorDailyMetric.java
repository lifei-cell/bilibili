package com.gary.bilibili.video.model;

import java.time.LocalDate;

public record CreatorDailyMetric(
        LocalDate statDate,
        long viewCount,
        long likeCount,
        long collectCount,
        long commentCount,
        long danmuCount) {
}
