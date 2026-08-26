package com.gary.bilibili.canal.controller;

import com.gary.bilibili.canal.service.SearchIndexMaintenanceService;
import com.gary.bilibili.common.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@RestController
@RequestMapping("/api/admin/reliability")
public class ReliabilityOperationsController {

    private final SearchIndexMaintenanceService maintenanceService;
    private final String adminToken;

    public ReliabilityOperationsController(
            SearchIndexMaintenanceService maintenanceService,
            @Value("${operations.admin-token:change-me-in-production}") String adminToken,
            Environment environment) {
        this.maintenanceService = maintenanceService;
        this.adminToken = adminToken;
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")
                && "change-me-in-production".equals(adminToken)) {
            throw new IllegalStateException("OPERATIONS_ADMIN_TOKEN is required in prod");
        }
    }

    @GetMapping("/cdc/reconcile")
    public Result<SearchIndexMaintenanceService.ReconciliationReport> reconcile(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authorize(token);
        return Result.ok(maintenanceService.reconcile());
    }

    @PostMapping("/es/rebuild")
    public Result<SearchIndexMaintenanceService.RebuildResult> rebuild(
            @RequestHeader(value = "X-Admin-Token", required = false) String token) {
        authorize(token);
        return Result.ok(maintenanceService.rebuild());
    }

    private void authorize(String token) {
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
    }
}
