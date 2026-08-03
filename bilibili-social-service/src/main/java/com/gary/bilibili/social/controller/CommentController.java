package com.gary.bilibili.social.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.social.dto.CommentCreateDTO;
import com.gary.bilibili.social.model.CommentPage;
import com.gary.bilibili.social.service.CommentService;
import com.gary.bilibili.social.vo.CommentCreateVO;
import com.gary.bilibili.social.vo.CommentListVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping({"/comment", "/api/comment"})
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping
    public Result<CommentCreateVO> create(@Valid @RequestBody CommentCreateDTO request) {
        return Result.ok(commentService.create(request));
    }

    @GetMapping("/list/{videoId}")
    public Result<List<CommentListVO>> getList(
            @PathVariable @Min(1) Long videoId,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size,
            @RequestParam(defaultValue = "hot")
            @Pattern(regexp = "hot|new") String sort) {
        CommentPage result = commentService.getList(videoId, page, size, sort);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @DeleteMapping("/{commentId}")
    public Result<Void> delete(@PathVariable @Min(1) Long commentId) {
        commentService.delete(commentId);
        return Result.ok();
    }
}
