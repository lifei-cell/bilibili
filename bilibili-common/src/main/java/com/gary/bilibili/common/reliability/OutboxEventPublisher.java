package com.gary.bilibili.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
@ConditionalOnProperty(name = {"spring.datasource.url", "rocketmq.producer.group"})
public class OutboxEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxEventPublisher.class);

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final String owner;
    private final int batchSize;
    private final int maxAttempts;
    private final int reclaimSeconds;
    private final int retrySeconds;
    private final Counter publishedCounter;
    private final Counter failedCounter;

    public OutboxEventPublisher(OutboxEventRepository repository,
                                ObjectMapper objectMapper,
                                RocketMQTemplate rocketMQTemplate,
                                MeterRegistry meterRegistry,
                                @Value("${spring.application.name:unknown-service}") String owner,
                                @Value("${reliability.outbox.batch-size:50}") int batchSize,
                                @Value("${reliability.outbox.max-attempts:100}") int maxAttempts,
                                @Value("${reliability.outbox.reclaim-seconds:60}") int reclaimSeconds,
                                @Value("${reliability.outbox.retry-seconds:2}") int retrySeconds) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.rocketMQTemplate = rocketMQTemplate;
        this.owner = owner;
        this.batchSize = Math.max(1, Math.min(500, batchSize));
        this.maxAttempts = Math.max(1, maxAttempts);
        this.reclaimSeconds = Math.max(10, reclaimSeconds);
        this.retrySeconds = Math.max(1, retrySeconds);
        this.publishedCounter = Counter.builder("bilibili.outbox.publish")
                .tag("owner", owner).tag("result", "success").register(meterRegistry);
        this.failedCounter = Counter.builder("bilibili.outbox.publish")
                .tag("owner", owner).tag("result", "failure").register(meterRegistry);
        Gauge.builder("bilibili.outbox.pending", repository,
                        value -> value.countPending(owner))
                .tag("owner", owner).register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "${reliability.outbox.dispatch-interval:500}",
            initialDelayString = "${reliability.outbox.initial-delay:3000}")
    public void dispatch() {
        List<OutboxEvent> events = repository.findDispatchable(
                owner, batchSize, reclaimSeconds, maxAttempts);
        events.forEach(this::publish);
    }

    private void publish(OutboxEvent event) {
        if (!repository.claim(event.id(), reclaimSeconds, maxAttempts)) {
            return;
        }
        try {
            Class<?> payloadType = Class.forName(event.payloadType());
            if (!payloadType.getName().startsWith("com.gary.bilibili.")) {
                throw new IllegalStateException("Unsupported outbox payload type");
            }
            Object payload = objectMapper.readValue(event.payload(), payloadType);
            rocketMQTemplate.syncSend(event.topic(), payload, 3000);
            repository.markPublished(event.id());
            publishedCounter.increment();
        } catch (Exception exception) {
            repository.markFailed(event.id(), safeMessage(exception),
                    LocalDateTime.now().plusSeconds(retrySeconds));
            failedCounter.increment();
            log.warn("Outbox dispatch failed, owner={}, eventId={}, topic={}, attempt={}",
                    owner, event.eventId(), event.topic(), event.attemptCount() + 1, exception);
        }
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName() : message;
    }
}
