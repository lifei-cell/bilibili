package com.gary.bilibili.video.service;

import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.model.MediaTranscodeResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Produces an adaptive HLS package and an automatically extracted cover. */
@Component
public class FfmpegVideoTranscodeWorker implements VideoTranscodeWorker {

    private static final Logger log = LoggerFactory.getLogger(FfmpegVideoTranscodeWorker.class);
    private static final List<Rendition> PROFILES = List.of(
            new Rendition("360P", 360, 800_000),
            new Rendition("720P", 720, 2_800_000),
            new Rendition("1080P", 1080, 5_000_000));

    private final HlsPackageStorage packageStorage;
    private final VideoEncoderSelector encoderSelector;
    private final String ffmpegPath;
    private final String ffprobePath;
    private final Path tempDirectory;
    private final long timeoutSeconds;
    private final String softwarePreset;
    private final int crf;
    private final int segmentSeconds;
    private final boolean hardwareFallback;
    private final boolean progressivePublish;

    public FfmpegVideoTranscodeWorker(
            HlsPackageStorage packageStorage,
            VideoEncoderSelector encoderSelector,
            @Value("${video.transcode.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${video.transcode.ffprobe-path:ffprobe}") String ffprobePath,
            @Value("${video.transcode.temp-dir}") String tempDirectory,
            @Value("${video.transcode.timeout-seconds:1800}") long timeoutSeconds,
            @Value("${video.transcode.software-preset:veryfast}") String softwarePreset,
            @Value("${video.transcode.crf:22}") int crf,
            @Value("${video.transcode.hls-segment-seconds:6}") int segmentSeconds,
            @Value("${video.transcode.hardware-fallback:true}") boolean hardwareFallback,
            @Value("${video.transcode.progressive-publish:true}") boolean progressivePublish) {
        this.packageStorage = packageStorage;
        this.encoderSelector = encoderSelector;
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
        this.tempDirectory = Path.of(tempDirectory);
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        this.softwarePreset = StringUtils.hasText(softwarePreset) ? softwarePreset : "veryfast";
        this.crf = Math.max(0, Math.min(51, crf));
        this.segmentSeconds = Math.max(2, segmentSeconds);
        this.hardwareFallback = hardwareFallback;
        this.progressivePublish = progressivePublish;
    }

    @Override
    public MediaTranscodeResult transcode(VideoTranscodeTask task,
                                          Consumer<MediaTranscodeResult> playableListener,
                                          Runnable assertLease) {
        validateTask(task);
        String taskKey = StringUtils.hasText(task.getFileMd5()) ? task.getFileMd5() : task.getTaskId();
        Path workDirectory = null;
        try {
            Files.createDirectories(tempDirectory);
            workDirectory = Files.createTempDirectory(tempDirectory, "video-hls-" + sanitize(taskKey) + "-");
            Path input = workDirectory.resolve("source");
            Path packageDirectory = workDirectory.resolve("hls");
            Files.createDirectories(packageDirectory);
            packageStorage.downloadSource(task, input);

            Dimensions source = probe(input, workDirectory.resolve("ffprobe.log"));
            boolean hasAudio = probeHasAudio(input, workDirectory.resolve("ffprobe-audio.log"));
            List<Rendition> renditions = selectRenditions(source.height());
            List<MediaTranscodeResult.Variant> variants = new ArrayList<>();
            String objectPrefix = packageStorage.objectPrefix(taskKey,
                    task.getClaimGeneration(), task.getClaimToken());
            VideoEncoder encoder = encoderSelector.select(workDirectory.resolve("ffmpeg-encoders.log"));

            extractCover(input, packageDirectory.resolve("cover.jpg"), workDirectory.resolve("ffmpeg-cover.log"));
            int firstBatchSize = progressivePublish && renditions.size() > 1 ? 1 : renditions.size();
            List<Rendition> firstBatch = renditions.subList(0, firstBatchSize);
            transcodeBatch(input, packageDirectory, firstBatch, source, hasAudio, encoder,
                    workDirectory.resolve("ffmpeg-first-batch.log"));
            variants.addAll(buildVariants(firstBatch, source, objectPrefix));

            if (firstBatchSize < renditions.size()) {
                MediaTranscodeResult playable = packageStorage.publish(packageDirectory, objectPrefix,
                        variants, assertLease);
                playableListener.accept(playable);

                List<Rendition> remaining = renditions.subList(firstBatchSize, renditions.size());
                transcodeBatch(input, packageDirectory, remaining, source, hasAudio, encoder,
                        workDirectory.resolve("ffmpeg-adaptive-batch.log"));
                variants.addAll(buildVariants(remaining, source, objectPrefix));
            }

            return packageStorage.publish(packageDirectory, objectPrefix, variants, assertLease);
        } catch (BusinessException exception) {
            throw exception;
        } catch (VideoTranscodeLeaseService.LeaseLostException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("视频转码被中断");
        } catch (Exception exception) {
            log.error("FFmpeg HLS transcode failed, taskId={}", task.getTaskId(), exception);
            throw new BusinessException("视频转码失败: " + safeMessage(exception));
        } finally {
            packageStorage.deleteTree(workDirectory);
        }
    }

    private void transcodeBatch(Path input,
                                Path packageDirectory,
                                List<Rendition> renditions,
                                Dimensions source,
                                boolean hasAudio,
                                VideoEncoder encoder,
                                Path logFile) throws Exception {
        List<HlsTranscodeCommandBuilder.Output> outputs = renditions.stream()
                .map(rendition -> new HlsTranscodeCommandBuilder.Output(
                        rendition.name(),
                        VideoScaleGeometry.fit(source.width(), source.height(), rendition.height())))
                .toList();
        prepareOutputDirectories(packageDirectory, outputs);
        try {
            run(HlsTranscodeCommandBuilder.build(
                    ffmpegPath, input, packageDirectory, outputs, hasAudio, encoder,
                    softwarePreset, crf, segmentSeconds), logFile, "FFmpeg HLS 转码失败");
        } catch (BusinessException exception) {
            if (!encoder.hardware() || !hardwareFallback) {
                throw exception;
            }
            log.warn("Hardware encoder {} failed; retrying batch with libx264", encoder.ffmpegName(), exception);
            clearOutputDirectories(packageDirectory, outputs);
            prepareOutputDirectories(packageDirectory, outputs);
            run(HlsTranscodeCommandBuilder.build(
                    ffmpegPath, input, packageDirectory, outputs, hasAudio, VideoEncoder.SOFTWARE,
                    softwarePreset, crf, segmentSeconds), logFile, "FFmpeg HLS 软件回退失败");
        }
    }

    private void extractCover(Path input, Path cover, Path logFile) throws Exception {
        try {
            run(coverCommand(input, cover, "1"), logFile, "封面截帧失败");
        } catch (BusinessException exception) {
            // Very short clips may not contain a frame at one second.
            run(coverCommand(input, cover, "0"), logFile, "封面截帧失败");
        }
        if (!Files.isRegularFile(cover) || Files.size(cover) == 0) throw new BusinessException("封面截帧失败");
    }

    private List<String> coverCommand(Path input, Path cover, String second) {
        return List.of(ffmpegPath, "-y", "-ss", second, "-i", input.toString(),
                "-frames:v", "1", "-vf", "scale=1280:-2:force_original_aspect_ratio=decrease",
                "-q:v", "2", cover.toString());
    }

    private List<MediaTranscodeResult.Variant> buildVariants(List<Rendition> renditions,
                                                              Dimensions source,
                                                              String objectPrefix) {
        return renditions.stream().map(rendition -> {
            VideoScaleGeometry geometry = VideoScaleGeometry.fit(
                    source.width(), source.height(), rendition.height());
            return packageStorage.variant(rendition.name(), geometry, rendition.bandwidth(), objectPrefix);
        }).toList();
    }

    private void prepareOutputDirectories(Path packageDirectory,
                                          List<HlsTranscodeCommandBuilder.Output> outputs) throws Exception {
        for (HlsTranscodeCommandBuilder.Output output : outputs) {
            Files.createDirectories(packageDirectory.resolve(output.name().toLowerCase()));
        }
    }

    private void clearOutputDirectories(Path packageDirectory,
                                        List<HlsTranscodeCommandBuilder.Output> outputs) {
        for (HlsTranscodeCommandBuilder.Output output : outputs) {
            packageStorage.deleteTree(packageDirectory.resolve(output.name().toLowerCase()));
        }
    }

    private Dimensions probe(Path input, Path logFile) throws Exception {
        Path output = logFile.resolveSibling("ffprobe.out");
        Process process = new ProcessBuilder(ffprobePath, "-v", "error", "-select_streams", "v:0",
                "-show_entries", "stream=width,height", "-of", "csv=p=0", input.toString())
                .redirectError(logFile.toFile()).redirectOutput(output.toFile()).start();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0)
            throw new BusinessException("无法读取视频分辨率");
        String[] values = Files.readString(output).trim().split(",");
        if (values.length < 2) throw new BusinessException("视频轨道不存在");
        return new Dimensions(Integer.parseInt(values[0]), Integer.parseInt(values[1]));
    }

    private boolean probeHasAudio(Path input, Path logFile) throws Exception {
        Path output = logFile.resolveSibling("ffprobe-audio.out");
        Process process = new ProcessBuilder(ffprobePath, "-v", "error", "-select_streams", "a:0",
                "-show_entries", "stream=index", "-of", "csv=p=0", input.toString())
                .redirectError(logFile.toFile()).redirectOutput(output.toFile()).start();
        if (!process.waitFor(60, TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new BusinessException("无法读取视频音频轨道");
        }
        return StringUtils.hasText(Files.readString(output));
    }

    private void run(List<String> command, Path logFile, String errorPrefix) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
        try {
            boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
            if (!completed) {
                throw new BusinessException("FFmpeg 转码超时");
            }
            if (process.exitValue() != 0) throw new BusinessException(errorPrefix + ": " + summarizeLog(logFile));
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
            }
        }
    }

    private List<Rendition> selectRenditions(int sourceHeight) {
        List<Rendition> selected = PROFILES.stream().filter(item -> item.height() <= sourceHeight).toList();
        if (!selected.isEmpty()) return selected;
        int height = Math.max(144, sourceHeight - sourceHeight % 2);
        return List.of(new Rendition(height + "P", height, 500_000));
    }

    private void validateTask(VideoTranscodeTask task) {
        if (task == null || (!StringUtils.hasText(task.getSourceObjectName()) && !StringUtils.hasText(task.getSourceUrl())))
            throw new BusinessException("转码源文件不存在");
        if (!StringUtils.hasText(task.getTaskId()) && !StringUtils.hasText(task.getFileMd5()))
            throw new BusinessException("转码任务标识不存在");
    }

    private String summarizeLog(Path logFile) throws Exception {
        if (!Files.exists(logFile)) return "无日志";
        String value = Files.readString(logFile, StandardCharsets.UTF_8).replaceAll("\\s+", " ").trim();
        return value.length() <= 300 ? value : value.substring(value.length() - 300);
    }

    private String safeMessage(Exception exception) {
        String value = StringUtils.hasText(exception.getMessage()) ? exception.getMessage() : exception.getClass().getSimpleName();
        value = value.replaceAll("\\s+", " ").trim();
        return value.length() <= 300 ? value : value.substring(0, 300);
    }

    private String sanitize(String value) { return value.replaceAll("[^a-zA-Z0-9._-]", "_"); }
    private record Rendition(String name, int height, int bandwidth) { }
    private record Dimensions(int width, int height) { }
}
