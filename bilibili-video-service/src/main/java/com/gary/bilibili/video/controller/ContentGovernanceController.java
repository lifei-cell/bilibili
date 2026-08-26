package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.dto.AuditActionDTO;
import com.gary.bilibili.video.dto.ReportResolveDTO;
import com.gary.bilibili.video.model.AdminVideoRow;
import com.gary.bilibili.video.model.AdminReportRow;
import com.gary.bilibili.video.model.GovernancePage;
import com.gary.bilibili.video.service.ContentGovernanceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;


@Validated
@RestController
@RequestMapping("/api/admin/content")
public class ContentGovernanceController {
    private final ContentGovernanceService service;
    public ContentGovernanceController(ContentGovernanceService service) { this.service = service; }

    @GetMapping("/videos")
    public Result<GovernancePage<AdminVideoRow>> videos(@RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.videos(status, page, size));
    }

    @PostMapping("/videos/{videoId}/audit")
    public Result<Void> audit(@PathVariable long videoId, @Valid @RequestBody AuditActionDTO request) {
        service.auditVideo(videoId, request); return Result.ok();
    }

    @GetMapping("/reports")
    public Result<GovernancePage<AdminReportRow>> reports(@RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(service.reports(status, page, size));
    }

    @PostMapping("/reports/{reportId}/resolve")
    public Result<Void> resolve(@PathVariable long reportId, @Valid @RequestBody ReportResolveDTO request) {
        service.resolveReport(reportId, request); return Result.ok();
    }
}
