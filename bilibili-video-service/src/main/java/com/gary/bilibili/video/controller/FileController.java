package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.dto.FileCheckDTO;
import com.gary.bilibili.video.dto.FileChunkUploadDTO;
import com.gary.bilibili.video.dto.FileMergeDTO;
import com.gary.bilibili.video.service.UploadService;
import com.gary.bilibili.video.vo.FileCheckVO;
import com.gary.bilibili.video.vo.FileMergeVO;
import com.gary.bilibili.video.vo.UploadChunkVO;
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
@RequestMapping({"/file", "/api/file"})
public class FileController {

    private final UploadService uploadService;

    public FileController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping("/check-md5")
    public Result<FileCheckVO> checkMd5(@Valid @RequestBody FileCheckDTO request) {
        return Result.ok(uploadService.checkFile(request));
    }

    @PostMapping("/chunk-upload")
    public Result<UploadChunkVO> uploadChunk(@Valid @ModelAttribute FileChunkUploadDTO request) {
        return Result.ok(uploadService.uploadFileChunk(request));
    }

    @PostMapping("/merge-chunks")
    public Result<FileMergeVO> mergeChunks(@Valid @RequestBody FileMergeDTO request) {
        return Result.ok(uploadService.mergeFile(request));
    }

    @GetMapping("/upload-progress/{md5}")
    public Result<UploadProgressVO> getUploadProgress(@PathVariable String md5) {
        return Result.ok(uploadService.getFileProgress(md5));
    }
}
