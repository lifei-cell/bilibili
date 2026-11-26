package com.gary.bilibili.video.vo;

public record CreatorTopVideoVO(
        long videoId,
        String title,
        String coverUrl,
        long viewCount,
        long likeCount,
        long collectCount,
        long commentCount,
        long danmuCount) {
}
