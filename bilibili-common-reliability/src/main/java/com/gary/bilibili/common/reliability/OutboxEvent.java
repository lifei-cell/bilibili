package com.gary.bilibili.common.reliability;

import java.time.LocalDateTime;

public record OutboxEvent(long id,
                          String eventId,
                          String owner,
                          String aggregateType,
                          String aggregateId,
                          String eventType,
                          String topic,
                          String payloadType,
                          String payload,
                          int attemptCount,
                          LocalDateTime createTime) {
}
