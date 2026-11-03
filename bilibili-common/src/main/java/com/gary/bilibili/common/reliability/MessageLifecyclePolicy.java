package com.gary.bilibili.common.reliability;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public record MessageLifecyclePolicy(
        @Value("${reliability.lifecycle.enabled:true}") boolean enabled,
        @Value("${reliability.lifecycle.outbox-retention-days:7}") int outboxDays,
        @Value("${reliability.lifecycle.inbox-retention-days:30}") int inboxDays,
        @Value("${reliability.lifecycle.batch-size:200}") int batchSize,
        @Value("${reliability.lifecycle.max-replays:5}") int maxReplays,
        @Value("${reliability.lifecycle.replay-cooldown-seconds:60}") int replayCooldownSeconds) {
    public MessageLifecyclePolicy {
        if (outboxDays < 1 || inboxDays < 1 || batchSize < 1 || batchSize > 1000
                || maxReplays < 1 || replayCooldownSeconds < 1) {
            throw new IllegalArgumentException("Invalid message lifecycle retention, batch or replay limits");
        }
    }
}
