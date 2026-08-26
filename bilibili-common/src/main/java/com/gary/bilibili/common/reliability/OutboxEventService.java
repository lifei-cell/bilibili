package com.gary.bilibili.common.reliability;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class OutboxEventService {

    private final OutboxEventRepository repository;
    private final ObjectMapper objectMapper;
    private final String owner;

    public OutboxEventService(OutboxEventRepository repository,
                              ObjectMapper objectMapper,
                              @Value("${spring.application.name:unknown-service}") String owner) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.owner = owner;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public String append(String aggregateType, String aggregateId, String eventType,
                         String topic, Object payload) {
        return append(UUID.randomUUID().toString(), aggregateType, aggregateId,
                eventType, topic, payload);
    }

    @Transactional
    public String appendStandalone(String aggregateType, String aggregateId, String eventType,
                                   String topic, Object payload) {
        return append(UUID.randomUUID().toString(), aggregateType, aggregateId,
                eventType, topic, payload);
    }

    @Transactional
    public String appendStandalone(String eventId, String aggregateType, String aggregateId,
                                   String eventType, String topic, Object payload) {
        return append(eventId, aggregateType, aggregateId, eventType, topic, payload);
    }

    private String append(String eventId, String aggregateType, String aggregateId,
                          String eventType, String topic, Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Outbox payload is required");
        }
        try {
            repository.insert(eventId, owner, aggregateType, aggregateId, eventType,
                    topic, payload.getClass().getName(), objectMapper.writeValueAsString(payload));
            return eventId;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize outbox payload", exception);
        }
    }
}
