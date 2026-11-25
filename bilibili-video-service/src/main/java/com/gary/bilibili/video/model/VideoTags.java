package com.gary.bilibili.video.model;

import com.gary.bilibili.common.exception.BusinessException;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class VideoTags {

    private VideoTags() {
    }

    public static String encode(List<String> tags) {
        Set<String> normalizedTags = new LinkedHashSet<>();
        for (String tag : tags) {
            String normalizedTag = tag.trim();
            if (!StringUtils.hasText(normalizedTag) || normalizedTag.contains(",")) {
                throw new BusinessException("请求参数错误");
            }
            normalizedTags.add(normalizedTag);
        }
        String value = String.join(",", normalizedTags);
        if (value.length() > 500) {
            throw new BusinessException("请求参数错误");
        }
        return value;
    }

    public static List<String> decode(String tags) {
        return StringUtils.hasText(tags) ? List.of(tags.split(",")) : List.of();
    }
}
