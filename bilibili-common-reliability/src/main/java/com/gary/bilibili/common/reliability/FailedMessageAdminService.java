package com.gary.bilibili.common.reliability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@ConditionalOnProperty(name = "spring.datasource.url")
public class FailedMessageAdminService {

    private final FailedMessageRepository repository;
    private final MessageReplayService replays;

    public FailedMessageAdminService(FailedMessageRepository repository, MessageReplayService replays) {
        this.repository = repository;
        this.replays = replays;
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

    public MessageReplayService.ReplayReceipt replay(long id, MessageReplayRequest request) {
        return replays.replayFailure(id, request);
    }

    public record FailedMessagePage(List<FailedMessage> records, long total) {
    }
}
