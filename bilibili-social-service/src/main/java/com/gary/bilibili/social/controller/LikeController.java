package com.gary.bilibili.social.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.social.dto.LikeDTO;
import com.gary.bilibili.social.service.LikeService;
import com.gary.bilibili.social.vo.LikeVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping({"/like", "/api/like"})
public class LikeController {

    private final LikeService likeService;

    public LikeController(LikeService likeService) {
        this.likeService = likeService;
    }

    @PostMapping
    public Result<LikeVO> like(@Valid @RequestBody LikeDTO request) {
        return Result.ok(likeService.like(request));
    }

    @DeleteMapping
    public Result<LikeVO> unlike(@Valid @RequestBody LikeDTO request) {
        return Result.ok(likeService.unlike(request));
    }

    @GetMapping("/status")
    public Result<LikeVO> getStatus(
            @RequestParam @Min(1) @Max(2) Integer targetType,
            @RequestParam @Min(1) Long targetId) {
        return Result.ok(likeService.getStatus(targetType, targetId));
    }
}
