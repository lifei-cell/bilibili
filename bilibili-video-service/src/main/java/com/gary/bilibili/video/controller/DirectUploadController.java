package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.dto.DirectUploadCompleteDTO;
import com.gary.bilibili.video.dto.DirectUploadInitDTO;
import com.gary.bilibili.video.service.DirectUploadService;
import com.gary.bilibili.video.vo.DirectUploadInitVO;
import com.gary.bilibili.video.vo.UploadMergeVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/upload/direct")
public class DirectUploadController {
    private final DirectUploadService service;

    public DirectUploadController(DirectUploadService service) { this.service = service; }

    @PostMapping("/init")
    public Result<DirectUploadInitVO> initiate(@Valid @RequestBody DirectUploadInitDTO request) {
        return Result.ok(service.initiate(request));
    }

    @PostMapping("/complete")
    public Result<UploadMergeVO> complete(@Valid @RequestBody DirectUploadCompleteDTO request) {
        return Result.ok(service.complete(request));
    }
}
