package com.gary.bilibili.video.task;

import com.gary.bilibili.video.model.CreatorDirtyDay;
import com.gary.bilibili.video.repository.CreatorAnalyticsRepository;
import com.gary.bilibili.video.service.CreatorAnalyticsAggregator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@ConditionalOnProperty(name = "creator.analytics.rebuild-enabled", havingValue = "true", matchIfMissing = true)
public class CreatorAnalyticsRebuildTask {

    private static final Logger log = LoggerFactory.getLogger(CreatorAnalyticsRebuildTask.class);

    private final CreatorAnalyticsRepository repository;
    private final CreatorAnalyticsAggregator aggregator;
    private final int batchSize;
    private final int leaseSeconds;

    public CreatorAnalyticsRebuildTask(CreatorAnalyticsRepository repository,
                                       CreatorAnalyticsAggregator aggregator,
                                       @Value("${creator.analytics.batch-size:50}") int batchSize,
                                       @Value("${creator.analytics.lease-seconds:60}") int leaseSeconds) {
        this.repository = repository;
        this.aggregator = aggregator;
        this.batchSize = Math.max(1, batchSize);
        this.leaseSeconds = Math.max(10, leaseSeconds);
    }

    @Scheduled(
            fixedDelayString = "${creator.analytics.rebuild-interval:30000}",
            initialDelayString = "${creator.analytics.rebuild-initial-delay:10000}")
    public void rebuildDirtyDays() {
        for (CreatorDirtyDay dirtyDay : repository.findDirtyDays(batchSize)) {
            rebuild(dirtyDay);
        }
    }

    private void rebuild(CreatorDirtyDay dirtyDay) {
        String token = UUID.randomUUID().toString();
        Long version = repository.claimDirtyDay(dirtyDay, token, leaseSeconds);
        if (version == null) {
            return;
        }
        try {
            aggregator.rebuildDay(dirtyDay.creatorId(), dirtyDay.statDate());
            repository.completeDirtyDay(dirtyDay, token, version);
        } catch (RuntimeException exception) {
            repository.failDirtyDay(dirtyDay, token, version, exception.getMessage());
            log.error("Creator analytics rebuild failed, creatorId={}, statDate={}",
                    dirtyDay.creatorId(), dirtyDay.statDate(), exception);
        }
    }
}
