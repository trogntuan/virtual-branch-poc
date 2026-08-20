package com.example.virtualbranch.storage;

import com.example.virtualbranch.config.StorageProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.InputStream;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "virtual-branch.storage.provider", havingValue = "s3", matchIfMissing = true)
public class S3ObjectStorageService implements ObjectStorageService {

    private final StorageProperties storageProperties;

    public S3ObjectStorageService(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void upload(String objectKey, InputStream inputStream, long size, String contentType) {
        try {
            buildClient().putObject(
                    PutObjectArgs.builder()
                            .bucket(storageProperties.bucket())
                            .object(objectKey)
                            .stream(inputStream, size, -1L)
                            .contentType(contentType)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageOperationException("S3 upload failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String presignGetUrl(String objectKey, int expirySeconds) {
        try {
            return buildClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(storageProperties.bucket())
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageOperationException("S3 presign failed: " + e.getMessage(), e);
        }
    }

    private MinioClient buildClient() {
        return MinioClient.builder()
                .endpoint(storageProperties.endpoint())
                .credentials(storageProperties.accessKey(), storageProperties.secretKey())
                .build();
    }
}
