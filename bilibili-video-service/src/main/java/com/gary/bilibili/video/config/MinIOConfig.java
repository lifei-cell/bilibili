package com.gary.bilibili.video.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import io.minio.SetBucketLifecycleArgs;
import io.minio.messages.AbortIncompleteMultipartUpload;
import io.minio.messages.Expiration;
import io.minio.messages.LifecycleConfiguration;
import io.minio.messages.LifecycleRule;
import io.minio.messages.RuleFilter;
import io.minio.messages.ResponseDate;
import io.minio.messages.Status;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import java.util.List;

@Configuration
public class MinIOConfig {

    @Bean
    @Primary
    public MinioClient minioClient(@Value("${minio.endpoint}") String endpoint,
                                   @Value("${minio.access-key}") String accessKey,
                                   @Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean("publicMinioClient")
    public MinioClient publicMinioClient(@Value("${minio.public-endpoint}") String endpoint,
                                         @Value("${minio.access-key}") String accessKey,
                                         @Value("${minio.secret-key}") String secretKey) {
        return MinioClient.builder().endpoint(endpoint).credentials(accessKey, secretKey).build();
    }

    @Bean
    public ApplicationRunner minioBucketInitializer(MinioClient minioClient,
                                                     @Value("${minio.video-bucket}") String videoBucket,
                                                     @Value("${minio.temp-bucket}") String tempBucket,
                                                     @Value("${video.transcode.output-prefix:play}") String outputPrefix,
                                                     @Value("${minio.lifecycle.temp-days:1}") int tempDays,
                                                     @Value("${minio.lifecycle.source-days:30}") int sourceDays,
                                                     @Value("${minio.public-read-enabled:true}") boolean publicReadEnabled) {
        return args -> {
            createBucketIfAbsent(minioClient, videoBucket);
            createBucketIfAbsent(minioClient, tempBucket);
            setLifecycle(minioClient, tempBucket, "", Math.max(1, tempDays), "expire-temp-upload");
            setLifecycle(minioClient, videoBucket, "source/", Math.max(1, sourceDays), "expire-source-media");
            if (publicReadEnabled) {
                minioClient.setBucketPolicy(SetBucketPolicyArgs.builder()
                        .bucket(videoBucket)
                        .config(publicReadPolicy(videoBucket, outputPrefix))
                        .build());
            }
        };
    }

    private void setLifecycle(MinioClient client, String bucket, String prefix, int days, String id) throws Exception {
        LifecycleRule rule = new LifecycleRule(Status.ENABLED,
                new AbortIncompleteMultipartUpload(1), new Expiration((ResponseDate) null, days, null),
                new RuleFilter(prefix), id, null, null, null);
        client.setBucketLifecycle(SetBucketLifecycleArgs.builder().bucket(bucket)
                .config(new LifecycleConfiguration(List.of(rule))).build());
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
