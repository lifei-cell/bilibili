package com.gary.bilibili.common.reliability;

import com.gary.bilibili.common.result.Result;
import jakarta.validation.Valid;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/mq/outbox")
@ConditionalOnProperty(name = "spring.datasource.url")
public class OutboxOperationsController {
    private final OperationsAuthorization authorization;
    private final OutboxOperationsRepository repository;
    private final MessageReplayService replays;

    public OutboxOperationsController(OperationsAuthorization authorization, OutboxOperationsRepository repository,
                                      MessageReplayService replays) {
        this.authorization = authorization;
        this.repository = repository;
        this.replays = replays;
    }

    @GetMapping({"/exhausted", "/archives"})
    public Result<List<OutboxOperationsRepository.OutboxSummary>> find(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "0") long afterId, @RequestParam(defaultValue = "20") int size,
            jakarta.servlet.http.HttpServletRequest request) {
        authorization.authorize(token);
        return Result.ok(repository.find(request.getRequestURI().endsWith("/archives"), afterId, size));
    }

    @PostMapping("/{id}/replay")
    public Result<MessageReplayService.ReplayReceipt> replay(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable long id, @Valid @RequestBody MessageReplayRequest request) {
        authorization.authorize(token);
        return Result.ok(replays.replayOutbox(id, request));
    }

    @GetMapping("/{id}/replays")
    public Result<List<OutboxOperationsRepository.ReplayAudit>> audit(
            @RequestHeader(value = "X-Admin-Token", required = false) String token, @PathVariable long id) {
        authorization.authorize(token);
        return Result.ok(repository.audit("OUTBOX", id));
    }
}
