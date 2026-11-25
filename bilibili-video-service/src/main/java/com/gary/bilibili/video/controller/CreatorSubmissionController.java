package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.dto.VideoResubmitDTO;
import com.gary.bilibili.video.model.GovernancePage;
import com.gary.bilibili.video.service.CreatorSubmissionService;
import com.gary.bilibili.video.vo.CreatorSubmissionVO;
import com.gary.bilibili.video.vo.VideoPublishVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/creator/submissions", "/api/creator/submissions"})
public class CreatorSubmissionController {

    private final CreatorSubmissionService submissionService;

    public CreatorSubmissionController(CreatorSubmissionService submissionService) {
        this.submissionService = submissionService;
    }

    @GetMapping
    public Result<GovernancePage<CreatorSubmissionVO>> list(
            @RequestParam(required = false) @Min(0) @Max(3) Integer status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return Result.ok(submissionService.list(status, page, size));
    }

    @PostMapping("/{videoId}/resubmit")
    public Result<VideoPublishVO> resubmit(@PathVariable @Min(1) long videoId,
                                           @Valid @RequestBody VideoResubmitDTO request) {
        return Result.ok(submissionService.resubmit(videoId, request));
    }
}
