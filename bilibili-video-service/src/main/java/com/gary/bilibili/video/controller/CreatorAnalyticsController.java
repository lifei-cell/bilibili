package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.service.CreatorAnalyticsService;
import com.gary.bilibili.video.vo.CreatorAnalyticsVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping({"/creator/analytics", "/api/creator/analytics"})
public class CreatorAnalyticsController {

    private final CreatorAnalyticsService analyticsService;

    public CreatorAnalyticsController(CreatorAnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping
    public Result<CreatorAnalyticsVO> dashboard(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return Result.ok(analyticsService.getDashboard(startDate, endDate, limit));
    }

    @PostMapping("/rebuild")
    public Result<CreatorAnalyticsVO> rebuild(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        return Result.ok(analyticsService.rebuild(startDate, endDate, limit));
    }
}
