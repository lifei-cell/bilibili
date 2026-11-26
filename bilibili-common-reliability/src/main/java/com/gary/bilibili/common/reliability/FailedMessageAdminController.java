package com.gary.bilibili.common.reliability;

import com.gary.bilibili.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/admin/mq/failures")
@ConditionalOnProperty(name = "spring.datasource.url")
public class FailedMessageAdminController {

    private final FailedMessageAdminService service;
    private final OperationsAuthorization authorization;
    private final OutboxOperationsRepository operations;

    public FailedMessageAdminController(FailedMessageAdminService service, OperationsAuthorization authorization,
                                        OutboxOperationsRepository operations) {
        this.service = service;
        this.authorization = authorization;
        this.operations = operations;
    }

    @GetMapping
    public Result<FailedMessageAdminService.FailedMessagePage> find(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "") String topic,
            @RequestParam(defaultValue = "FAILED") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        authorization.authorize(token);
        return Result.ok(service.find(topic, status, page, size));
    }

    @PostMapping("/{id}/replay")
    public Result<MessageReplayService.ReplayReceipt> replay(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable long id, @Valid @RequestBody MessageReplayRequest request) {
        authorization.authorize(token);
        return Result.ok(service.replay(id, request));
    }

    @GetMapping("/{id}/replays")
    public Result<List<OutboxOperationsRepository.ReplayAudit>> audit(
            @RequestHeader(value = "X-Admin-Token", required = false) String token, @PathVariable long id) {
        authorization.authorize(token);
        return Result.ok(operations.audit("DLQ", id));
    }
}
