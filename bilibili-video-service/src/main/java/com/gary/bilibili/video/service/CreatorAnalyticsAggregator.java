package com.gary.bilibili.video.service;

import com.gary.bilibili.video.repository.CreatorAnalyticsRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
public class CreatorAnalyticsAggregator {

    private final CreatorAnalyticsRepository repository;

    public CreatorAnalyticsAggregator(CreatorAnalyticsRepository repository) {
        this.repository = repository;
    }

    @Transactional(rollbackFor = Exception.class)
    public void rebuildDay(long creatorId, LocalDate statDate) {
        repository.lockCreator(creatorId);
        repository.replaceDay(creatorId, statDate);
    }
}
