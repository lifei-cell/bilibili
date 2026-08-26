package com.gary.bilibili.common.reliability;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class FailedMessageAdminService {

    private final FailedMessageRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider;
    private final Counter replayCounter;

    public FailedMessageAdminService(FailedMessageRepository repository,
                                     ObjectMapper objectMapper,
                                     ObjectProvider<RocketMQTemplate> rocketMQTemplateProvider,
                                     MeterRegistry meterRegistry) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.rocketMQTemplateProvider = rocketMQTemplateProvider;
        this.replayCounter = Counter.builder("bilibili.mq.dlq.replay")
                .description("Application DLQ replay operations")
                .register(meterRegistry);
    }

    public FailedMessagePage find(String topic, String status, int page, int size) {
        String safeTopic = topic == null ? "" : topic.trim();
        String requestedStatus = status == null ? "" : status.trim().toUpperCase();
        String safeStatus = switch (requestedStatus) {
            case "REPLAYED", "RESOLVED" -> requestedStatus;
            default -> "FAILED";
        };
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        List<FailedMessage> records = repository.findFailures(
                safeTopic, safeStatus, (safePage - 1) * safeSize, safeSize);
        return new FailedMessagePage(records, repository.countFailures(safeTopic, safeStatus));
    }

    public FailedMessage replay(long id) {
        FailedMessage failed = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Failed message not found"));
        if (!"FAILED".equals(failed.status())) {
            throw new IllegalStateException("Only FAILED messages can be replayed");
        }
        try {
            Class<?> payloadType = Class.forName(failed.payloadType());
            if (!payloadType.getName().startsWith("com.gary.bilibili.")) {
                throw new IllegalStateException("Unsupported replay payload type");
            }
            Object payload = objectMapper.readValue(failed.payload(), payloadType);
            RocketMQTemplate rocketMQTemplate = rocketMQTemplateProvider.getIfAvailable();
            if (rocketMQTemplate == null) {
                throw new IllegalStateException("RocketMQ is unavailable for replay");
            }
            rocketMQTemplate.syncSend(failed.topic(), payload, 3000);
            if (!repository.markReplayed(id)) {
                throw new IllegalStateException("Failed message state changed during replay");
            }
            replayCounter.increment();
            return repository.findById(id).orElseThrow();
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Cannot load replay payload type", exception);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed message replay failed", exception);
        }
    }

    public record FailedMessagePage(List<FailedMessage> records, long total) {
    }
}
