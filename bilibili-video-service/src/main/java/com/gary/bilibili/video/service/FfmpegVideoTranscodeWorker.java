package com.gary.bilibili.video.service;

import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.video.entity.VideoTranscodeTask;
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
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Converts the uploaded source into a browser-friendly MP4 and stores the
 * result in MinIO. The worker is deliberately behind a small interface so a
 * remote media-processing service can replace FFmpeg later.
 */
@Component
public class FfmpegVideoTranscodeWorker implements VideoTranscodeWorker {

    private static final Logger log = LoggerFactory.getLogger(FfmpegVideoTranscodeWorker.class);

    private final MinioClient minioClient;
    private final String videoBucket;
    private final String publicEndpoint;
    private final String ffmpegPath;
    private final Path tempDirectory;
    private final long timeoutSeconds;
    private final String outputPrefix;

    public FfmpegVideoTranscodeWorker(
            MinioClient minioClient,
            @Value("${minio.video-bucket}") String videoBucket,
            @Value("${minio.public-endpoint}") String publicEndpoint,
            @Value("${video.transcode.ffmpeg-path:ffmpeg}") String ffmpegPath,
            @Value("${video.transcode.temp-dir}") String tempDirectory,
            @Value("${video.transcode.timeout-seconds:1800}") long timeoutSeconds,
            @Value("${video.transcode.output-prefix:play}") String outputPrefix) {
        this.minioClient = minioClient;
        this.videoBucket = videoBucket;
        this.publicEndpoint = publicEndpoint;
        this.ffmpegPath = ffmpegPath;
        this.tempDirectory = Path.of(tempDirectory);
        this.timeoutSeconds = Math.max(1, timeoutSeconds);
        String normalizedOutputPrefix = trimSlashes(outputPrefix);
        this.outputPrefix = normalizedOutputPrefix.isBlank() ? "play" : normalizedOutputPrefix;
    }

    @Override
    public String transcode(VideoTranscodeTask task) {
        validateTask(task);
        String taskKey = StringUtils.hasText(task.getFileMd5())
                ? task.getFileMd5()
                : task.getTaskId();
        Path workDirectory = null;
        try {
            Files.createDirectories(tempDirectory);
            workDirectory = Files.createTempDirectory(
                    tempDirectory, "video-transcode-" + sanitize(taskKey) + "-");
            Path input = workDirectory.resolve("source");
            Path output = workDirectory.resolve("play.mp4");
            Path logFile = workDirectory.resolve("ffmpeg.log");

            downloadSource(task, input);
            runFfmpeg(input, output, logFile);
            if (!Files.isRegularFile(output) || Files.size(output) == 0) {
                throw new BusinessException("FFmpeg 未生成有效视频文件");
            }

            String objectName = outputPrefix + "/" + taskKey + ".mp4";
            try (InputStream outputStream = Files.newInputStream(output)) {
                minioClient.putObject(PutObjectArgs.builder()
                        .bucket(videoBucket)
                        .object(objectName)
                        .stream(outputStream, Files.size(output), -1)
                        .contentType("video/mp4")
                        .build());
            }
            return buildPublicUrl(objectName);
        } catch (BusinessException exception) {
            throw exception;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new BusinessException("视频转码被中断");
        } catch (Exception exception) {
            log.error("FFmpeg transcode failed, taskId={}", task.getTaskId(), exception);
            throw new BusinessException("视频转码失败: " + safeMessage(exception));
        } finally {
            deleteWorkDirectory(workDirectory);
        }
    }

    private void validateTask(VideoTranscodeTask task) {
        if (task == null
                || (!StringUtils.hasText(task.getSourceObjectName())
                && !StringUtils.hasText(task.getSourceUrl()))) {
            throw new BusinessException("转码源文件不存在");
        }
        if (!StringUtils.hasText(task.getTaskId())
                && !StringUtils.hasText(task.getFileMd5())) {
            throw new BusinessException("转码任务标识不存在");
        }
    }

    private void downloadSource(VideoTranscodeTask task, Path target) throws Exception {
        if (StringUtils.hasText(task.getSourceObjectName())) {
            try (InputStream source = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(videoBucket)
                    .object(task.getSourceObjectName())
                    .build())) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(task.getSourceUrl()))
                    .timeout(Duration.ofMinutes(5))
                    .GET()
                    .build();
            HttpResponse<Path> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                Files.deleteIfExists(target);
                throw new BusinessException("下载转码源文件失败，HTTP " + response.statusCode());
            }
        }
        if (!Files.isRegularFile(target) || Files.size(target) == 0) {
            throw new BusinessException("转码源文件为空");
        }
    }

    private void runFfmpeg(Path input, Path output, Path logFile)
            throws Exception {
        List<String> command = List.of(
                ffmpegPath,
                "-y",
                "-i", input.toString(),
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "23",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-movflags", "+faststart",
                output.toString());
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(logFile.toFile())
                .start();
        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        if (!completed) {
            process.destroy();
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
            throw new BusinessException("FFmpeg 转码超时");
        }
        if (process.exitValue() != 0) {
            throw new BusinessException("FFmpeg 执行失败: " + summarizeLog(logFile));
        }
    }

    private String buildPublicUrl(String objectName) {
        String endpoint = trimTrailingSlash(publicEndpoint);
        return endpoint + "/" + videoBucket + "/" + objectName;
    }

    private String summarizeLog(Path logFile) throws Exception {
        if (!Files.exists(logFile)) {
            return "无日志";
        }
        String value = Files.readString(logFile, StandardCharsets.UTF_8)
                .replaceAll("\\s+", " ")
                .trim();
        return value.length() <= 300 ? value : value.substring(value.length() - 300);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        String normalized = message.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }

    private String sanitize(String value) {
        return value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String trimSlashes(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("^/+|/+$", "");
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.replaceAll("/+$", "");
    }

    private void deleteWorkDirectory(Path workDirectory) {
        if (workDirectory == null || !Files.exists(workDirectory)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(workDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception exception) {
                    log.warn("Delete transcode temporary file failed, path={}", path, exception);
                }
            });
        } catch (Exception exception) {
            log.warn("Delete transcode temporary directory failed, path={}",
                    workDirectory, exception);
        }
    }
}
