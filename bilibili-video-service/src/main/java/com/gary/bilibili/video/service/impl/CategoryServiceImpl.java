package com.gary.bilibili.video.service.impl;

import com.gary.bilibili.video.entity.VideoCategory;
import com.gary.bilibili.video.mapper.VideoCategoryMapper;
import com.gary.bilibili.video.service.CategoryService;
import com.gary.bilibili.video.vo.VideoCategoryVO;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    private final VideoCategoryMapper categoryMapper;

    public CategoryServiceImpl(VideoCategoryMapper categoryMapper) {
        this.categoryMapper = categoryMapper;
    }

    @Override
    public List<VideoCategoryVO> listEnabled() {
        List<VideoCategory> categories = categoryMapper.selectEnabledCategories();
        if (categories == null || categories.isEmpty()) {
            return Collections.emptyList();
        }
        return categories.stream().map(this::toVO).toList();
    }

    private VideoCategoryVO toVO(VideoCategory category) {
        VideoCategoryVO result = new VideoCategoryVO();
        result.setId(category.getId());
        result.setParentId(category.getParentId());
        result.setName(category.getName());
        result.setSort(category.getSort());
        return result;
    }
}
