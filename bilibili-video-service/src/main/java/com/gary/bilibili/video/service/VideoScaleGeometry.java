package com.gary.bilibili.video.service;

/** FFmpeg/libx264 output geometry constrained to dimensions supported by yuv420p. */
record VideoScaleGeometry(int width, int height) {

    static VideoScaleGeometry fit(int sourceWidth, int sourceHeight, int targetHeight) {
        if (sourceWidth <= 0 || sourceHeight <= 0 || targetHeight <= 0) {
            throw new IllegalArgumentException("Video dimensions must be positive");
        }
        int evenHeight = Math.max(2, targetHeight - targetHeight % 2);
        int scaledWidth = (int) Math.round(sourceWidth * (double) evenHeight / sourceHeight);
        int evenWidth = Math.max(2, scaledWidth - scaledWidth % 2);
        return new VideoScaleGeometry(evenWidth, evenHeight);
    }

    String ffmpegFilter() {
        return "scale=" + width + ":" + height;
    }
}
