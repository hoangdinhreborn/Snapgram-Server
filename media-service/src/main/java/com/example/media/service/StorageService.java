package com.example.media.service;

import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class StorageService {

    private final MinioClient minioClient;

    @Getter
    @Value("${app.minio.bucket}")
    private String bucket;

    @Value("${app.minio.public-url}")
    private String publicUrl;

    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .stream(inputStream, size, -1)
                            .contentType(contentType)
                            .build()
            );
            log.info("Uploaded object to MinIO: key={}, size={} bytes", objectKey, size);
        } catch (Exception e) {
            log.error("Failed to upload object to MinIO: key={}, error={}", objectKey, e.getMessage());
            throw new RuntimeException("Storage upload failed: " + e.getMessage(), e);
        }
    }

    public void delete(String objectKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucket)
                            .object(objectKey)
                            .build()
            );
            log.info("Deleted object from MinIO: key={}", objectKey);
        } catch (Exception e) {
            log.warn("Failed to delete object from MinIO: key={}, error={}", objectKey, e.getMessage());
        }
    }

    public String generatePresignedUploadUrl(String objectKey, String contentType, int expirationSeconds) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucket)
                            .object(objectKey)
                            .expiry(expirationSeconds, TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            log.error("Failed to generate presigned upload URL: key={}, error={}", objectKey, e.getMessage());
            throw new RuntimeException("Could not generate upload URL: " + e.getMessage(), e);
        }
    }

    public String buildPublicUrl(String objectKey) {
        if (publicUrl.endsWith("/")) {
            return publicUrl + objectKey;
        }
        return publicUrl + "/" + objectKey;
    }
}
