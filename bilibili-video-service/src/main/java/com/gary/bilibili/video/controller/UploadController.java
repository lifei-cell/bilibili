package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.dto.UploadCheckDTO;
import com.gary.bilibili.video.dto.UploadChunkDTO;
import com.gary.bilibili.video.dto.UploadMergeDTO;
import com.gary.bilibili.video.service.UploadService;
import com.gary.bilibili.video.vo.UploadCheckVO;
import com.gary.bilibili.video.vo.UploadChunkVO;
import com.gary.bilibili.video.vo.UploadMergeVO;
import com.gary.bilibili.video.vo.UploadProgressVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping({"/upload", "/api/upload"})
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/check")
    public Result<UploadCheckVO> check(@Valid @RequestBody UploadCheckDTO request) {
        return Result.ok(uploadService.check(request));
    }

    @PostMapping("/chunk")
    public Result<UploadChunkVO> uploadChunk(@Valid @ModelAttribute UploadChunkDTO request) {
        return Result.ok(uploadService.uploadChunk(request));
    }

    @PostMapping("/merge")
    public Result<UploadMergeVO> merge(@Valid @RequestBody UploadMergeDTO request) {
        return Result.ok(uploadService.merge(request));
    }

    @GetMapping("/progress/{uploadId}")
    public Result<UploadProgressVO> getProgress(@PathVariable String uploadId) {
        return Result.ok(uploadService.getProgress(uploadId));
    }
}
