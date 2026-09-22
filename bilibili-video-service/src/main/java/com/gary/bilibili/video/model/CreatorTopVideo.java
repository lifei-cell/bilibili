package com.gary.bilibili.video.model;

public record CreatorTopVideo(
        long videoId,
        String title,
        String coverUrl,
        long viewCount,
        long likeCount,
        long collectCount,
        long commentCount,
        long danmuCount) {
}
