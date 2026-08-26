package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.dto.ContentReportDTO;
import com.gary.bilibili.video.service.ContentGovernanceService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/report")
public class ContentReportController {
    private final ContentGovernanceService service;
    public ContentReportController(ContentGovernanceService service) { this.service = service; }

    @PostMapping
    public Result<Map<String, Long>> report(@Valid @RequestBody ContentReportDTO request) {
        return Result.ok(Map.of("reportId", service.report(request)));
    }
}
