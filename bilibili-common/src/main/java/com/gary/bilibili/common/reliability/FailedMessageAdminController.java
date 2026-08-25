package com.gary.bilibili.common.reliability;

import com.gary.bilibili.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@RestController
@RequestMapping("/api/admin/mq/failures")
@ConditionalOnProperty(name = "spring.datasource.url")
public class FailedMessageAdminController {

    private final FailedMessageAdminService service;
    private final String adminToken;

    public FailedMessageAdminController(FailedMessageAdminService service,
                                        @Value("${operations.admin-token:change-me-in-production}")
                                        String adminToken,
                                        Environment environment) {
        this.service = service;
        this.adminToken = adminToken;
        if (java.util.Arrays.asList(environment.getActiveProfiles()).contains("prod")
                && "change-me-in-production".equals(adminToken)) {
            throw new IllegalStateException("OPERATIONS_ADMIN_TOKEN is required in prod");
        }
    }

    @GetMapping
    public Result<FailedMessageAdminService.FailedMessagePage> find(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "") String topic,
            @RequestParam(defaultValue = "FAILED") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        authorize(token);
        return Result.ok(service.find(topic, status, page, size));
    }

    @PostMapping("/{id}/replay")
    public Result<FailedMessage> replay(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable long id) {
        authorize(token);
        return Result.ok(service.replay(id));
    }

    private void authorize(String token) {
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
    }
}
