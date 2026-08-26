package com.gary.bilibili.video.service;

import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
import com.gary.bilibili.video.model.MediaTranscodeResult;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/** Produces an adaptive HLS package and an automatically extracted cover. */
@Component
public class FfmpegVideoTranscodeWorker implements VideoTranscodeWorker {

    private static final Logger log = LoggerFactory.getLogger(FfmpegVideoTranscodeWorker.class);
    private static final List<Rendition> PROFILES = List.of(
            new Rendition("360P", 360, 800_000),
            new Rendition("720P", 720, 2_800_000),
            new Rendition("1080P", 1080, 5_000_000));

    private final MinioClient minioClient;
    private final String videoBucket;
    private final String deliveryEndpoint;
    private final String ffmpegPath;
    private final String ffprobePath;
    private final Path tempDirectory;
    private final long timeoutSeconds;
    private final String outputPrefix;

    public FfmpegVideoTranscodeWorker(
            MinioClient minioClient,
            @Value("${minio.video-bucket}") String videoBucket,
            @Value("${cdn.base-url:${minio.public-endpoint}}") String deliveryEndpoint,
            @Value("${video.transcode.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${video.transcode.ffprobe-path:ffprobe}") String ffprobePath,
            @Value("${video.transcode.temp-dir}") String tempDirectory,
            @Value("${video.transcode.timeout-seconds:1800}") long timeoutSeconds,
            @Value("${video.transcode.output-prefix:play}") String outputPrefix) {
        this.minioClient = minioClient;
        this.videoBucket = videoBucket;
        this.deliveryEndpoint = trimTrailingSlash(deliveryEndpoint);
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
        this.tempDirectory = Path.of(tempDirectory);
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        String normalized = trimSlashes(outputPrefix);
        this.outputPrefix = normalized.isBlank() ? "play" : normalized;
    }

    @Override
    public MediaTranscodeResult transcode(VideoTranscodeTask task) {
        validateTask(task);
        String taskKey = StringUtils.hasText(task.getFileMd5()) ? task.getFileMd5() : task.getTaskId();
        Path workDirectory = null;
        try {
            Files.createDirectories(tempDirectory);
            workDirectory = Files.createTempDirectory(tempDirectory, "video-hls-" + sanitize(taskKey) + "-");
            Path input = workDirectory.resolve("source");
            Path packageDirectory = workDirectory.resolve("hls");
            Files.createDirectories(packageDirectory);
            downloadSource(task, input);

            Dimensions source = probe(input, workDirectory.resolve("ffprobe.log"));
            List<Rendition> renditions = selectRenditions(source.height());
            List<MediaTranscodeResult.Variant> variants = new ArrayList<>();
            String objectPrefix = outputPrefix + "/" + taskKey;
            for (Rendition rendition : renditions) {
                int width = evenWidth(source, rendition.height());
                Path renditionDirectory = packageDirectory.resolve(rendition.name().toLowerCase());
                Files.createDirectories(renditionDirectory);
                transcodeRendition(input, renditionDirectory, rendition,
                        workDirectory.resolve("ffmpeg-" + rendition.name() + ".log"));
                variants.add(new MediaTranscodeResult.Variant(
                        rendition.name(), width, rendition.height(), rendition.bandwidth(),
                        buildDeliveryUrl(objectPrefix + "/" + rendition.name().toLowerCase() + "/index.m3u8")));
            }

            Files.writeString(packageDirectory.resolve("master.m3u8"), buildMasterPlaylist(variants), StandardCharsets.UTF_8);
            extractCover(input, packageDirectory.resolve("cover.jpg"), workDirectory.resolve("ffmpeg-cover.log"));
            uploadPackage(packageDirectory, objectPrefix);
            return new MediaTranscodeResult(
                    buildDeliveryUrl(objectPrefix + "/master.m3u8"),
                    buildDeliveryUrl(objectPrefix + "/cover.jpg"), List.copyOf(variants));
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("视频转码被中断");
        } catch (Exception exception) {
            log.error("FFmpeg HLS transcode failed, taskId={}", task.getTaskId(), exception);
            throw new BusinessException("视频转码失败: " + safeMessage(exception));
        } finally {
            deleteWorkDirectory(workDirectory);
        }
    }

    private void transcodeRendition(Path input, Path outputDirectory, Rendition rendition, Path logFile)
            throws Exception {
        run(List.of(ffmpegPath, "-y", "-i", input.toString(),
                "-vf", "scale=-2:" + rendition.height() + ":force_original_aspect_ratio=decrease",
                "-c:v", "libx264", "-preset", "veryfast", "-profile:v", "main",
                "-crf", "22", "-g", "48", "-keyint_min", "48", "-sc_threshold", "0",
                "-c:a", "aac", "-b:a", "128k", "-ar", "48000",
                "-hls_time", "6", "-hls_playlist_type", "vod",
                "-hls_segment_filename", outputDirectory.resolve("seg_%05d.ts").toString(),
                outputDirectory.resolve("index.m3u8").toString()), logFile, "FFmpeg HLS 转码失败");
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

    private void run(List<String> command, Path logFile, String errorPrefix) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(logFile.toFile()).start();
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly();
            throw new BusinessException("FFmpeg 转码超时");
        }
        if (process.exitValue() != 0) throw new BusinessException(errorPrefix + ": " + summarizeLog(logFile));
    }

    private List<Rendition> selectRenditions(int sourceHeight) {
        List<Rendition> selected = PROFILES.stream().filter(item -> item.height() <= sourceHeight).toList();
        if (!selected.isEmpty()) return selected;
        int height = Math.max(144, sourceHeight - sourceHeight % 2);
        return List.of(new Rendition(height + "P", height, 500_000));
    }

    private int evenWidth(Dimensions source, int height) {
        int width = (int) Math.round(source.width() * (double) height / source.height());
        return Math.max(2, width - width % 2);
    }

    private String buildMasterPlaylist(List<MediaTranscodeResult.Variant> variants) {
        StringBuilder value = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (MediaTranscodeResult.Variant variant : variants) {
            value.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(variant.bandwidth())
                    .append(",RESOLUTION=").append(variant.width()).append('x').append(variant.height())
                    .append("\n").append(variant.quality().toLowerCase()).append("/index.m3u8\n");
        }
        return value.toString();
    }

    private void uploadPackage(Path packageDirectory, String objectPrefix) throws Exception {
        try (Stream<Path> paths = Files.walk(packageDirectory)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                String relative = packageDirectory.relativize(path).toString().replace('\\', '/');
                try (InputStream stream = Files.newInputStream(path)) {
                    minioClient.putObject(PutObjectArgs.builder().bucket(videoBucket)
                            .object(objectPrefix + "/" + relative).stream(stream, Files.size(path), -1)
                            .contentType(contentType(path)).headers(cacheHeaders(path)).build());
                }
            }
        }
    }

    private String contentType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".m3u8")) return "application/vnd.apple.mpegurl";
        if (name.endsWith(".ts")) return "video/mp2t";
        if (name.endsWith(".jpg")) return "image/jpeg";
        return "application/octet-stream";
    }

    private Map<String, String> cacheHeaders(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith("master.m3u8")) return Map.of("Cache-Control", "public,max-age=60");
        if (name.endsWith(".m3u8")) return Map.of("Cache-Control", "public,max-age=300");
        if (name.endsWith(".ts")) return Map.of("Cache-Control", "public,max-age=31536000,immutable");
        return Map.of("Cache-Control", "public,max-age=86400");
    }

    private void validateTask(VideoTranscodeTask task) {
        if (task == null || (!StringUtils.hasText(task.getSourceObjectName()) && !StringUtils.hasText(task.getSourceUrl())))
            throw new BusinessException("转码源文件不存在");
        if (!StringUtils.hasText(task.getTaskId()) && !StringUtils.hasText(task.getFileMd5()))
            throw new BusinessException("转码任务标识不存在");
    }

    private void downloadSource(VideoTranscodeTask task, Path target) throws Exception {
        if (StringUtils.hasText(task.getSourceObjectName())) {
            try (InputStream source = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(videoBucket).object(task.getSourceObjectName()).build())) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
            HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(task.getSourceUrl()))
                    .timeout(Duration.ofMinutes(5)).GET().build(), HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() < 200 || response.statusCode() >= 300)
                throw new BusinessException("下载转码源文件失败，HTTP " + response.statusCode());
        }
        if (!Files.isRegularFile(target) || Files.size(target) == 0) throw new BusinessException("转码源文件为空");
    }

    private String buildDeliveryUrl(String objectName) { return deliveryEndpoint + "/" + videoBucket + "/" + objectName; }

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
    private String trimSlashes(String value) { return StringUtils.hasText(value) ? value.replaceAll("^/+|/+$", "") : ""; }
    private String trimTrailingSlash(String value) { return StringUtils.hasText(value) ? value.replaceAll("/+$", "") : ""; }

    private void deleteWorkDirectory(Path workDirectory) {
        if (workDirectory == null || !Files.exists(workDirectory)) return;
        try (Stream<Path> paths = Files.walk(workDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); }
                catch (Exception exception) { log.warn("Delete transcode temp file failed, path={}", path, exception); }
            });
        } catch (Exception exception) { log.warn("Delete transcode temp directory failed, path={}", workDirectory, exception); }
    }

    private record Rendition(String name, int height, int bandwidth) { }
    private record Dimensions(int width, int height) { }
}
