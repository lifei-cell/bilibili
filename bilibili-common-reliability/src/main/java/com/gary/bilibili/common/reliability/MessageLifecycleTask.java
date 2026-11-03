package com.gary.bilibili.common.reliability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = {"spring.datasource.url", "rocketmq.producer.group"})
public class MessageLifecycleTask {
    private static final Logger log = LoggerFactory.getLogger(MessageLifecycleTask.class);
    private final MessageArchiveRepository archive;
    private final MessageLifecyclePolicy policy;
    private final String owner;
    private final Counter outboxArchived;
    private final Counter inboxCompacted;
    private final Counter failures;
    private final AtomicLong lastSuccess = new AtomicLong(Instant.now().getEpochSecond());

    public MessageLifecycleTask(MessageArchiveRepository archive, MessageLifecyclePolicy policy,
                                MeterRegistry meters, @Value("${spring.application.name}") String owner) {
        this.archive = archive;
        this.policy = policy;
        this.owner = owner;
        outboxArchived = meters.counter("bilibili.lifecycle.archived", "kind", "outbox");
        inboxCompacted = meters.counter("bilibili.lifecycle.archived", "kind", "inbox");
        failures = meters.counter("bilibili.lifecycle.failures");
        Gauge.builder("bilibili.lifecycle.enabled", policy, p -> p.enabled() ? 1 : 0).register(meters);
        Gauge.builder("bilibili.lifecycle.last.success.seconds", lastSuccess, AtomicLong::doubleValue).register(meters);
    }

    @Scheduled(fixedDelayString = "${reliability.lifecycle.interval-ms:60000}",
            initialDelayString = "${reliability.lifecycle.initial-delay-ms:60000}")
    public void run() {
        if (!policy.enabled()) return;
        try {
            outboxArchived.increment(archive.archivePublished(owner, policy.outboxDays(), policy.batchSize()));
            inboxCompacted.increment(archive.compactInbox(policy.inboxDays(), policy.batchSize()));
            lastSuccess.set(Instant.now().getEpochSecond());
        } catch (RuntimeException failure) {
            failures.increment();
            log.error("Message lifecycle batch failed, owner={}", owner, failure);
        }
    }
}
