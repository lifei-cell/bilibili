package com.gary.bilibili.video.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MinIOConfig {

    @Bean
    public MinioClient minioClient(@Value("${minio.endpoint}") String endpoint,
                                   @Value("${minio.access-key}") String accessKey,
                                   @Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient minioClient,
                                                     @Value("${minio.video-bucket}") String videoBucket,
                                                     @Value("${minio.temp-bucket}") String tempBucket,
                                                     @Value("${video.transcode.output-prefix:play}") String outputPrefix,
                                                     @Value("${minio.public-read-enabled:true}") boolean publicReadEnabled) {
        return args -> {
            createBucketIfAbsent(minioClient, videoBucket);
            createBucketIfAbsent(minioClient, tempBucket);
            if (publicReadEnabled) {
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(videoBucket)
                        .config(publicReadPolicy(videoBucket, outputPrefix))
                        .build());
            }
        };
    }

    private void createBucketIfAbsent(MinioClient minioClient, String bucket) throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }
    }

    private String publicReadPolicy(String bucket, String outputPrefix) {
        String prefix = outputPrefix == null || outputPrefix.isBlank() ? "play" : outputPrefix;
        prefix = prefix.replaceAll("^/+|/+$", "");
        if (prefix.isBlank()) {
            prefix = "play";
        }
        return """
                {
                  "Version": "2012-10-17",
                  "Statement": [
                    {
                      "Effect": "Allow",
                      "Principal": { "AWS": ["*"] },
                      "Action": ["s3:GetObject"],
                      "Resource": ["arn:aws:s3:::%s/%s/*"]
                    }
                  ]
                }
                """.formatted(bucket, prefix);
    }
}
