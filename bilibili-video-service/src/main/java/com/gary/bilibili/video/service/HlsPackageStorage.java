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
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/** Owns source retrieval, HLS package publication, delivery URLs, and temporary-tree cleanup. */
@Component
class HlsPackageStorage {

    private static final Logger log = LoggerFactory.getLogger(HlsPackageStorage.class);

    private final MinioClient minioClient;
    private final String videoBucket;
    private final String deliveryEndpoint;
    private final String outputPrefix;

    HlsPackageStorage(MinioClient minioClient,
                      @Value("${minio.video-bucket}") String videoBucket,
                      @Value("${cdn.base-url:${minio.public-endpoint}}") String deliveryEndpoint,
                      @Value("${video.transcode.output-prefix:play}") String outputPrefix) {
        this.minioClient = minioClient;
        this.videoBucket = videoBucket;
        this.deliveryEndpoint = trimTrailingSlash(deliveryEndpoint);
        String normalized = trimSlashes(outputPrefix);
        this.outputPrefix = normalized.isBlank() ? "play" : normalized;
    }

    String objectPrefix(String taskKey) {
        return outputPrefix + "/" + taskKey;
    }

    MediaTranscodeResult.Variant variant(String quality,
                                         VideoScaleGeometry geometry,
                                         int bandwidth,
                                         String objectPrefix) {
        return new MediaTranscodeResult.Variant(
                quality, geometry.width(), geometry.height(), bandwidth,
                deliveryUrl(objectPrefix + "/" + quality.toLowerCase() + "/index.m3u8"));
    }

    MediaTranscodeResult publish(Path packageDirectory,
                                 String objectPrefix,
                                 List<MediaTranscodeResult.Variant> variants) throws Exception {
        Files.writeString(packageDirectory.resolve("master.m3u8"),
                masterPlaylist(variants), StandardCharsets.UTF_8);
        uploadPackage(packageDirectory, objectPrefix);
        return new MediaTranscodeResult(
                deliveryUrl(objectPrefix + "/master.m3u8"),
                deliveryUrl(objectPrefix + "/cover.jpg"),
                List.copyOf(variants));
    }

    void downloadSource(VideoTranscodeTask task, Path target) throws Exception {
        if (StringUtils.hasText(task.getSourceObjectName())) {
            try (InputStream source = minioClient.getObject(GetObjectArgs.builder()
                    .bucket(videoBucket).object(task.getSourceObjectName()).build())) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
            HttpResponse<Path> response = client.send(HttpRequest.newBuilder(URI.create(task.getSourceUrl()))
                    .timeout(Duration.ofMinutes(5)).GET().build(), HttpResponse.BodyHandlers.ofFile(target));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new BusinessException("下载转码源文件失败，HTTP " + response.statusCode());
            }
        }
        if (!Files.isRegularFile(target) || Files.size(target) == 0) {
            throw new BusinessException("转码源文件为空");
        }
    }

    void deleteTree(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception exception) {
                    log.warn("Delete transcode temporary file failed, path={}", path, exception);
                }
            });
        } catch (Exception exception) {
            log.warn("Delete transcode temporary directory failed, path={}", root, exception);
        }
    }

    private void uploadPackage(Path packageDirectory, String objectPrefix) throws Exception {
        try (Stream<Path> paths = Files.walk(packageDirectory)) {
            List<Path> files = paths.filter(Files::isRegularFile)
                    .sorted(Comparator.comparingInt(this::publicationOrder))
                    .toList();
            for (Path path : files) {
                String relative = packageDirectory.relativize(path).toString().replace('\\', '/');
                try (InputStream stream = Files.newInputStream(path)) {
                    minioClient.putObject(PutObjectArgs.builder().bucket(videoBucket)
                            .object(objectPrefix + "/" + relative).stream(stream, Files.size(path), -1)
                            .contentType(contentType(path)).headers(cacheHeaders(path)).build());
                }
            }
        }
    }

    private int publicationOrder(Path path) {
        return "master.m3u8".equalsIgnoreCase(path.getFileName().toString()) ? 1 : 0;
    }

    private String masterPlaylist(List<MediaTranscodeResult.Variant> variants) {
        StringBuilder value = new StringBuilder("#EXTM3U\n#EXT-X-VERSION:3\n");
        for (MediaTranscodeResult.Variant variant : variants) {
            value.append("#EXT-X-STREAM-INF:BANDWIDTH=").append(variant.bandwidth())
                    .append(",RESOLUTION=").append(variant.width()).append('x').append(variant.height())
                    .append("\n").append(variant.quality().toLowerCase()).append("/index.m3u8\n");
        }
        return value.toString();
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

    private String deliveryUrl(String objectName) {
        return deliveryEndpoint + "/" + videoBucket + "/" + objectName;
    }

    private String trimSlashes(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("^/+|/+$", "") : "";
    }

    private String trimTrailingSlash(String value) {
        return StringUtils.hasText(value) ? value.replaceAll("/+$", "") : "";
    }
}
