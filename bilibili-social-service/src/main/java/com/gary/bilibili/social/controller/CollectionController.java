package com.gary.bilibili.social.controller;

import com.gary.bilibili.common.result.Result;
import com.gary.bilibili.social.dto.CollectionDTO;
import com.gary.bilibili.social.dto.CollectionFolderCreateDTO;
import com.gary.bilibili.social.dto.CollectionFolderUpdateDTO;
import com.gary.bilibili.social.model.CollectionPage;
import com.gary.bilibili.social.service.CollectionService;
import com.gary.bilibili.social.vo.CollectionFolderVO;
import com.gary.bilibili.social.vo.CollectionListVO;
import com.gary.bilibili.social.vo.CollectionVO;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
@RequestMapping({"/collection", "/api/collection"})
public class CollectionController {

    private final CollectionService collectionService;

    public CollectionController(CollectionService collectionService) {
        this.collectionService = collectionService;
    }

    @PostMapping
    public Result<CollectionVO> collect(@Valid @RequestBody CollectionDTO request) {
        return Result.ok(collectionService.collect(request));
    }

    @DeleteMapping
    public Result<CollectionVO> uncollect(@Valid @RequestBody CollectionDTO request) {
        return Result.ok(collectionService.uncollect(request));
    }

    @GetMapping("/list")
    public Result<List<CollectionListVO>> getList(
            @RequestParam(required = false) @Min(0) Long folderId,
            @RequestParam(defaultValue = "1") @Min(1) Integer page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) Integer size) {
        CollectionPage result = collectionService.getList(folderId, page, size);
        return Result.ok(result.getRecords(), result.getTotal());
    }

    @PostMapping("/folder")
    public Result<CollectionFolderVO> createFolder(
            @Valid @RequestBody CollectionFolderCreateDTO request) {
        return Result.ok(collectionService.createFolder(request));
    }

    @PutMapping("/folder/{folderId}")
    public Result<Void> updateFolder(
            @PathVariable @Min(1) Long folderId,
            @Valid @RequestBody CollectionFolderUpdateDTO request) {
        collectionService.updateFolder(folderId, request);
        return Result.ok();
    }

    @GetMapping("/folders")
    public Result<List<CollectionFolderVO>> getFolders() {
        List<CollectionFolderVO> result = collectionService.getFolders();
        return Result.ok(result, (long) result.size());
    }
}
