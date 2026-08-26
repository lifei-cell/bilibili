package com.gary.bilibili.common.reliability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class ReliableMessageExecutor {

    private static final Logger log = LoggerFactory.getLogger(ReliableMessageExecutor.class);

    private final FailedMessageRepository repository;
    private final MessageInboxRepository inboxRepository;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;
    private final int maxAttempts;
    private final long initialBackoffMillis;
    private final int inboxLeaseSeconds;
    private final boolean rethrowAfterFailure;

    public ReliableMessageExecutor(FailedMessageRepository repository,
                                   MessageInboxRepository inboxRepository,
                                   ObjectMapper objectMapper,
                                   MeterRegistry meterRegistry,
                                   @Value("${reliability.mq.max-attempts:3}") int maxAttempts,
                                   @Value("${reliability.mq.initial-backoff:100ms}")
                                   java.time.Duration initialBackoff,
                                   @Value("${reliability.mq.inbox-lease-seconds:60}")
                                   int inboxLeaseSeconds,
                                   @Value("${reliability.mq.rethrow-after-failure:true}")
                                   boolean rethrowAfterFailure) {
        this.repository = repository;
        this.inboxRepository = inboxRepository;
        this.objectMapper = objectMapper;
        this.meterRegistry = meterRegistry;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMillis = Math.max(0, initialBackoff.toMillis());
        this.inboxLeaseSeconds = Math.max(10, inboxLeaseSeconds);
        this.rethrowAfterFailure = rethrowAfterFailure;
        Gauge.builder("bilibili.mq.dlq.pending", repository, FailedMessageRepository::countPending)
                .description("Persisted failed MQ messages awaiting replay")
                .register(meterRegistry);
    }

    public void execute(String topic, String consumerGroup, String messageKey,
                        Object payload, Runnable action) {
        String serializedPayload = serialize(payload);
        if (!inboxRepository.acquire(topic, consumerGroup, messageKey, inboxLeaseSeconds)) {
            counter("bilibili.mq.consume", topic, consumerGroup, "duplicate").increment();
            return;
        }
        Throwable lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                action.run();
                inboxRepository.markSucceeded(topic, consumerGroup, messageKey);
                repository.markResolved(topic, consumerGroup, messageKey, serializedPayload);
                counter("bilibili.mq.consume", topic, consumerGroup, "success").increment();
                return;
            } catch (RuntimeException failure) {
                lastFailure = failure;
                counter("bilibili.mq.consume", topic, consumerGroup, "retry").increment();
                if (attempt < maxAttempts) {
                    backoff(attempt);
                }
            }
        }

        inboxRepository.markFailed(topic, consumerGroup, messageKey, lastFailure);
        repository.saveFailure(topic, consumerGroup, messageKey,
                payload.getClass().getName(), serializedPayload, lastFailure,
                MDC.get("traceId"), maxAttempts);
        counter("bilibili.mq.consume", topic, consumerGroup, "dlq").increment();
        log.error("MQ message persisted to application DLQ topic={} group={} messageKey={} attempts={}",
                topic, consumerGroup, messageKey, maxAttempts, lastFailure);
        if (rethrowAfterFailure) {
            throw new MessageProcessingException(
                    "MQ message processing failed after application retries", lastFailure);
        }
    }

    private Counter counter(String name, String topic, String group, String result) {
        return Counter.builder(name)
                .tag("topic", topic).tag("consumer_group", group).tag("result", result)
                .register(meterRegistry);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            return "{\"serializationError\":\"" + exception.getClass().getSimpleName() + "\"}";
        }
    }

    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(initialBackoffMillis * attempt, 2000));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("MQ retry interrupted", exception);
        }
    }

    public static class MessageProcessingException extends RuntimeException {

        public MessageProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
