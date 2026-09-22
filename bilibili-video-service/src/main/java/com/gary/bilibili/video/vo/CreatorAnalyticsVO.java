package com.gary.bilibili.video.vo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreatorAnalyticsVO(
        LocalDate startDate,
        LocalDate endDate,
        CreatorAnalyticsTotalVO totals,
        List<CreatorDailyTrendVO> trend,
        List<CreatorTopVideoVO> topVideos,
        LocalDateTime lastAggregatedAt) {
}
