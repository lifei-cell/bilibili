package com.gary.bilibili.video.vo;

import java.time.LocalDate;

public record CreatorDailyTrendVO(
        LocalDate date,
        long viewCount,
        long likeCount,
        long collectCount,
        long commentCount,
        long danmuCount) {
}
