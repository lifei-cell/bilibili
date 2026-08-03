package com.gary.bilibili.search.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.search.model.SearchPage;
import com.gary.bilibili.search.service.SearchService;
import com.gary.bilibili.search.vo.VideoSearchVO;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping({"/search", "/api/search"})
public class SearchController {

    private final SearchService searchService;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping({"", "/video"})
    public Result<List<VideoSearchVO>> searchVideo(
            @RequestParam @Size(max = 100) String keyword,
            @RequestParam(required = false) @Min(1) Long categoryId,
            @RequestParam(defaultValue = "default")
            @Pattern(regexp = "default|hot|new") String sort,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        SearchPage result = searchService.searchVideo(keyword, categoryId, sort, page, size);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @GetMapping("/hot")
    public Result<List<String>> getHotSearch(
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer size) {
        List<String> result = searchService.getHotSearch(size);
        return Result.ok(result, (long) result.size());
    }

    @GetMapping("/suggest")
    public Result<List<String>> getSuggestions(
            @RequestParam @Size(max = 100) String keyword,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) Integer size) {
        List<String> result = searchService.getSuggestions(keyword, size);
        return Result.ok(result, (long) result.size());
    }
}
