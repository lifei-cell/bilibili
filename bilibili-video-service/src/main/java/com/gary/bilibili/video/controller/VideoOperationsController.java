package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.service.VideoPublicationOperationsService;
import com.gary.bilibili.video.service.VideoRenditionRecoveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@RestController
@RequestMapping("/api/admin/video")
public class VideoOperationsController {

    private final VideoPublicationOperationsService publicationService;
    private final VideoRenditionRecoveryService renditionRecoveryService;
    private final String adminToken;

    public VideoOperationsController(
            VideoPublicationOperationsService publicationService,
            VideoRenditionRecoveryService renditionRecoveryService,
            @Value("${operations.admin-token:change-me-in-production}") String adminToken,
            Environment environment) {
        this.publicationService = publicationService;
        this.renditionRecoveryService = renditionRecoveryService;
        this.adminToken = adminToken;
        if (Arrays.asList(environment.getActiveProfiles()).contains("prod")
                && "change-me-in-production".equals(adminToken)) {
            throw new IllegalStateException("OPERATIONS_ADMIN_TOKEN is required in prod");
        }
    }

    @PostMapping("/{videoId}/publish")
    public Result<VideoPublicationOperationsService.PublicationResult> publish(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable long videoId) {
        authorize(token);
        return Result.ok(publicationService.publish(videoId));
    }

    @PostMapping("/transcode/{taskId}/retry-renditions")
    public Result<Void> retryRenditions(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @PathVariable String taskId) {
        authorize(token);
        renditionRecoveryService.requeue(taskId);
        return Result.ok();
    }

    private void authorize(String token) {
        if (token == null || !MessageDigest.isEqual(
                token.getBytes(StandardCharsets.UTF_8),
                adminToken.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token");
        }
    }
}
