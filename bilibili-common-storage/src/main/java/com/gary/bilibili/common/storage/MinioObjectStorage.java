package com.gary.bilibili.common.storage;

import io.minio.ComposeObjectArgs;
import io.minio.ComposeSource;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/** MinIO SDK boundary. Missing objects are distinct from authorization and transport failures. */
@Component
public class MinioObjectStorage implements ObjectStorage {
    private final MinioClient client;

    public MinioObjectStorage(MinioClient client) { this.client = client; }

    @Override
    public void put(String bucket, String objectName, InputStream content, long size, String contentType) throws Exception {
        client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectName)
                .stream(content, size, -1).contentType(contentType).build());
    }

    @Override
    public boolean exists(String bucket, String objectName) throws Exception {
        try {
            client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectName).build());
            return true;
        } catch (ErrorResponseException failure) {
            String code = failure.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) return false;
            throw failure;
        }
    }

    @Override
    public void compose(String sourceBucket, List<String> sourceObjects, String targetBucket, String targetObject) throws Exception {
        List<ComposeSource> sources = sourceObjects.stream()
                .map(name -> ComposeSource.builder().bucket(sourceBucket).object(name).build()).toList();
        client.composeObject(ComposeObjectArgs.builder().bucket(targetBucket).object(targetObject).sources(sources).build());
    }
}
