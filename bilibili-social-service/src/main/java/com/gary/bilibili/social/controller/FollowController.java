package com.gary.bilibili.social.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.social.model.UserPage;
import com.gary.bilibili.social.service.FollowService;
import com.gary.bilibili.social.vo.FollowStatusVO;
import com.gary.bilibili.social.vo.FollowVO;
import com.gary.bilibili.social.vo.UserBriefVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping({"/follow", "/api/follow"})
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    @PostMapping("/{userId}")
    public Result<FollowVO> follow(@PathVariable @Min(1) Long userId) {
        return Result.ok(followService.follow(userId));
    }

    @DeleteMapping("/{userId}")
    public Result<FollowVO> unfollow(@PathVariable @Min(1) Long userId) {
        return Result.ok(followService.unfollow(userId));
    }

    @GetMapping("/status/{userId}")
    public Result<FollowStatusVO> getStatus(@PathVariable @Min(1) Long userId) {
        return Result.ok(followService.getStatus(userId));
    }

    @GetMapping("/following/{userId}")
    public Result<List<UserBriefVO>> getFollowing(
            @PathVariable @Min(1) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        UserPage result = followService.getFollowing(userId, page, size);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @GetMapping("/follower/{userId}")
    public Result<List<UserBriefVO>> getFollowers(
            @PathVariable @Min(1) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        UserPage result = followService.getFollowers(userId, page, size);
        return Result.ok(result.getRecords(), result.getTotal());
    }
}
