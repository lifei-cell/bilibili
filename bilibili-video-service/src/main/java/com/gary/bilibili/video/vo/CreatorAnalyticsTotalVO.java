package com.gary.bilibili.video.vo;

public record CreatorAnalyticsTotalVO(
        long viewCount,
        long likeCount,
        long collectCount,
        long commentCount,
        long danmuCount) {
}
