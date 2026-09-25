package com.example.media.config;

import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.SetBucketPolicyArgs;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class MinioConfig {

    @Value("${app.minio.endpoint}")
    private String endpoint;

    @Value("${app.minio.access-key}")
    private String accessKey;

    @Value("${app.minio.secret-key}")
    private String secretKey;

    @Value("${app.minio.bucket}")
    private String bucket;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }

    @Bean
    public ApplicationRunner initMinioBucket(MinioClient minioClient) {
        return args -> {
            try {
                boolean exists = minioClient.bucketExists(
                        BucketExistsArgs.builder().bucket(bucket).build()
                );
                if (!exists) {
                    log.info("Creating MinIO bucket: {}", bucket);
                    minioClient.makeBucket(
                            MakeBucketArgs.builder().bucket(bucket).build()
                    );
                    setPublicReadPolicy(minioClient, bucket);
                    log.info("MinIO bucket '{}' created and set to public read", bucket);
                } else {
                    log.info("MinIO bucket '{}' already exists", bucket);
                }
            } catch (Exception e) {
                log.error("Failed to initialize MinIO bucket '{}': {}", bucket, e.getMessage());
            }
        };
    }

    private void setPublicReadPolicy(MinioClient minioClient, String bucket) {
        try {
            String policy = """
                    {
                      "Version": "2012-10-17",
                      "Statement": [
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetBucketLocation", "s3:ListBucket"],
                          "Resource": ["arn:aws:s3:::%s"]
                        },
                        {
                          "Effect": "Allow",
                          "Principal": {"AWS": ["*"]},
                          "Action": ["s3:GetObject"],
                          "Resource": ["arn:aws:s3:::%s/*"]
                        }
                      ]
                    }
                    """.formatted(bucket, bucket);

            minioClient.setBucketPolicy(
                    SetBucketPolicyArgs.builder()
                            .bucket(bucket)
                            .config(policy)
                            .build()
            );
        } catch (Exception e) {
            log.warn("Could not set public policy for bucket {}: {}", bucket, e.getMessage());
        }
    }
}
