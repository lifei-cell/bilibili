package com.gary.bilibili.video.service;

import java.util.Locale;

enum VideoEncoder {
    SOFTWARE("libx264", false),
    NVIDIA("h264_nvenc", true),
    INTEL_QSV("h264_qsv", true),
    VAAPI("h264_vaapi", true);

    private final String ffmpegName;
    private final boolean hardware;

    VideoEncoder(String ffmpegName, boolean hardware) {
        this.ffmpegName = ffmpegName;
        this.hardware = hardware;
    }

    String ffmpegName() {
        return ffmpegName;
    }

    boolean hardware() {
        return hardware;
    }

    static VideoEncoder fromConfiguration(String value) {
        String normalized = value == null ? "auto" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "software", "cpu", "libx264" -> SOFTWARE;
            case "nvidia", "nvenc", "h264_nvenc" -> NVIDIA;
            case "intel", "qsv", "h264_qsv" -> INTEL_QSV;
            case "vaapi", "h264_vaapi" -> VAAPI;
            case "auto", "" -> null;
            default -> throw new IllegalArgumentException("Unsupported video encoder: " + value);
        };
    }
}
