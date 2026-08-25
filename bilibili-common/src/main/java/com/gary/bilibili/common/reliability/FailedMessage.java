package com.gary.bilibili.common.reliability;

import java.time.LocalDateTime;

public record FailedMessage(
        Long id,
        String topic,
        String consumerGroup,
        String messageKey,
        String payloadType,
        String payload,
        String exceptionType,
        String errorMessage,
        String traceId,
        int attemptCount,
        String status,
        int replayCount,
        LocalDateTime createTime,
        LocalDateTime updateTime,
        LocalDateTime lastReplayTime) {
}
