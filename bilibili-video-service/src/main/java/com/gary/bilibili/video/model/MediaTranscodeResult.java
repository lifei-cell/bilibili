package com.gary.bilibili.video.model;

import java.util.List;

public record MediaTranscodeResult(
        String masterUrl,
        String coverUrl,
        List<Variant> variants) {

    public record Variant(String quality, int width, int height, int bandwidth, String url) {
    }
}
