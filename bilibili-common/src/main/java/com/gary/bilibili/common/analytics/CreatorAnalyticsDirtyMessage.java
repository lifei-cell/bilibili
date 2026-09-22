package com.gary.bilibili.common.analytics;

import java.time.LocalDate;

public record CreatorAnalyticsDirtyMessage(String eventId,
                                           Long videoId,
                                           LocalDate statDate) {

    public static final String TOPIC = "creator-analytics-dirty";
}
