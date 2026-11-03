package com.gary.bilibili.video.service.upload;

import com.gary.bilibili.common.exception.BusinessException;
import com.gary.bilibili.common.storage.ObjectStorage;
import com.gary.bilibili.video.dto.UploadChunkDTO;
import com.gary.bilibili.video.entity.FileChunk;
import com.gary.bilibili.video.model.UploadTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Locale;

/** Upload-specific naming and error translation, independent of the MinIO SDK. */
@Component
public class UploadStorage {
    private static final Logger log = LoggerFactory.getLogger(UploadStorage.class);
    private final ObjectStorage objects;
    private final String publicEndpoint;
    private final String videoBucket;
    private final String tempBucket;

    public UploadStorage(ObjectStorage objects, @Value("${minio.public-endpoint}") String publicEndpoint,
                         @Value("${minio.video-bucket}") String videoBucket,
                         @Value("${minio.temp-bucket}") String tempBucket) {
        this.objects = objects;
        this.publicEndpoint = publicEndpoint.endsWith("/")
                ? publicEndpoint.substring(0, publicEndpoint.length() - 1) : publicEndpoint;
        this.videoBucket = videoBucket;
        this.tempBucket = tempBucket;
    }

    public void putChunk(UploadChunkDTO request, String objectName) {
        try (InputStream input = request.getFile().getInputStream()) {
            objects.put(tempBucket, objectName, input, request.getFile().getSize(), "application/octet-stream");
        } catch (Exception failure) {
            log.error("Upload chunk failed, uploadId={}, chunkIndex={}", request.getUploadId(), request.getChunkIndex(), failure);
            throw new BusinessException("分片上传失败");
        }
    }

    public void composeChunks(List<FileChunk> chunks, String objectName) {
        try {
            if (!objects.exists(videoBucket, objectName)) {
                objects.compose(tempBucket, chunks.stream().map(FileChunk::getObjectName).toList(), videoBucket, objectName);
            }
        } catch (Exception failure) {
            log.error("Merge chunks failed, objectName={}", objectName, failure);
            throw new BusinessException("分片合并失败");
        }
    }

    public String buildSourceObjectName(UploadTask task) {
        String fileName = task.getFileName();
        int dot = fileName.lastIndexOf('.');
        String extension = dot < 0 ? "" : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return "source/" + task.getFileMd5() + (extension.matches("[a-z0-9]{1,10}") ? "." + extension : "");
    }

    public String sourceUrl(String objectName) { return publicEndpoint + "/" + videoBucket + "/" + objectName; }
}
