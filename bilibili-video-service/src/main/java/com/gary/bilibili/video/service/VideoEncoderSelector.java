package com.gary.bilibili.video.service;

import com.gary.bilibili.common.exception.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Resolves an explicitly configured encoder or an actually exposed GPU device. */
@Component
class VideoEncoderSelector {

    private static final Logger log = LoggerFactory.getLogger(VideoEncoderSelector.class);

    private final String ffmpegPath;
    private final String configuredEncoder;

    VideoEncoderSelector(@Value("${video.transcode.ffmpeg-path:ffmpeg}") String ffmpegPath,
                         @Value("${video.transcode.encoder:auto}") String configuredEncoder) {
        this.ffmpegPath = ffmpegPath;
        this.configuredEncoder = configuredEncoder;
    }

    VideoEncoder select(Path logFile) {
        VideoEncoder configured;
        try {
            configured = VideoEncoder.fromConfiguration(configuredEncoder);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(exception.getMessage());
        }
        if (configured != null) {
            return configured;
        }

        Set<String> encoders = detectEncoders(logFile);
        // NVIDIA CUDA base images commonly define NVIDIA_VISIBLE_DEVICES=all even
        // when Compose did not attach a GPU. The device node is the reliable
        // runtime signal; otherwise auto mode must remain portable and use CPU.
        if (encoders.contains(VideoEncoder.NVIDIA.ffmpegName())
                && Files.isReadable(Path.of("/dev/nvidia0"))) {
            return VideoEncoder.NVIDIA;
        }
        if (Files.exists(Path.of("/dev/dri/renderD128"))) {
            if (encoders.contains(VideoEncoder.INTEL_QSV.ffmpegName())) return VideoEncoder.INTEL_QSV;
            if (encoders.contains(VideoEncoder.VAAPI.ffmpegName())) return VideoEncoder.VAAPI;
        }
        return VideoEncoder.SOFTWARE;
    }

    private Set<String> detectEncoders(Path logFile) {
        try {
            Process process = new ProcessBuilder(ffmpegPath, "-hide_banner", "-encoders")
                    .redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
            if (!process.waitFor(10, TimeUnit.SECONDS) || process.exitValue() != 0) {
                process.destroyForcibly();
                return Set.of();
            }
            String output = Files.readString(logFile, StandardCharsets.UTF_8);
            return Stream.of(VideoEncoder.values())
                    .map(VideoEncoder::ffmpegName)
                    .filter(output::contains)
                    .collect(Collectors.toUnmodifiableSet());
        } catch (Exception exception) {
            log.warn("Detect FFmpeg hardware encoders failed; using libx264", exception);
            return Set.of();
        }
    }
}
