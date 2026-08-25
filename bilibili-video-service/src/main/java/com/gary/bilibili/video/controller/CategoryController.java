package com.gary.bilibili.video.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.video.service.CategoryService;
import com.gary.bilibili.video.vo.VideoCategoryVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/category", "/api/category"})
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping("/list")
    public Result<List<VideoCategoryVO>> list() {
        List<VideoCategoryVO> categories = categoryService.listEnabled();
        return Result.ok(categories, (long) categories.size());
    }
}
