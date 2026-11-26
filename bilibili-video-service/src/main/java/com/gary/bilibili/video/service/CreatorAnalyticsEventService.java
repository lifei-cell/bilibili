package com.gary.bilibili.video.service;

import com.gary.bilibili.video.repository.CreatorAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
public class CreatorAnalyticsEventService {

    private final CreatorAnalyticsRepository repository;

    public CreatorAnalyticsEventService(CreatorAnalyticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(rollbackFor = Exception.class)
    public void recordView(String requestId, long videoId, LocalDateTime occurredAt) {
        LocalDateTime eventTime = occurredAt == null ? LocalDateTime.now() : occurredAt;
        if (repository.insertViewEvent(requestId, videoId, eventTime)) {
            repository.markDirtyForVideo(videoId, eventTime.toLocalDate());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public void markDirty(long videoId, LocalDate statDate) {
        repository.markDirtyForVideo(videoId, statDate);
    }
}
