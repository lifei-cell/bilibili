package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.dto.VideoPublishDTO;
import com.gary.bilibili.video.dto.VideoUpdateDTO;
import com.gary.bilibili.video.model.VideoPage;
import com.gary.bilibili.video.service.VideoService;
import com.gary.bilibili.video.vo.VideoDetailVO;
import com.gary.bilibili.video.vo.VideoListVO;
import com.gary.bilibili.video.vo.VideoPlayVO;
import com.gary.bilibili.video.vo.VideoPublishVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping({"/video", "/api/video"})
public class VideoController {

    private final VideoService videoService;

    public VideoController(VideoService videoService) {
        this.videoService = videoService;
    }

    @PostMapping("/publish")
    public Result<VideoPublishVO> publish(@Valid @RequestBody VideoPublishDTO request) {
        return Result.ok(videoService.publish(request));
    }

    @GetMapping("/{videoId}")
    public Result<VideoDetailVO> getDetail(@PathVariable @Min(1) Long videoId) {
        return Result.ok(videoService.getDetail(videoId));
    }

    @GetMapping("/{videoId}/play")
    public Result<VideoPlayVO> getPlayInfo(@PathVariable @Min(1) Long videoId) {
        return Result.ok(videoService.getPlayInfo(videoId));
    }

    @GetMapping("/list")
    public Result<List<VideoListVO>> getList(
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(required = false) @Min(1) Long categoryId,
            @RequestParam(defaultValue = "default")
            @Pattern(regexp = "default|hot|new") String sort) {
        VideoPage result = videoService.getList(page, size, categoryId, sort);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @GetMapping("/user/{userId}")
    public Result<List<VideoListVO>> getUserVideos(
            @PathVariable @Min(1) Long userId,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "default")
            @Pattern(regexp = "default|hot|new") String sort) {
        VideoPage result = videoService.getUserVideos(userId, page, size, sort);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @PutMapping("/{videoId}")
    public Result<Void> update(@PathVariable @Min(1) Long videoId,
                               @Valid @RequestBody VideoUpdateDTO request) {
        videoService.update(videoId, request);
        return Result.ok();
    }

    @DeleteMapping("/{videoId}")
    public Result<Void> delete(@PathVariable @Min(1) Long videoId) {
        videoService.delete(videoId);
        return Result.ok();
    }
}
