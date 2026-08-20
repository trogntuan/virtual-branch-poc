package com.example.virtualbranch.storage;

import com.example.virtualbranch.config.EgressStorageProperties;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.Http.Method;
import io.minio.MinioClient;
import org.springframework.stereotype.Service;

@Service
public class EgressPlaybackUrlService {

    private final EgressStorageProperties egressStorageProperties;

    public EgressPlaybackUrlService(EgressStorageProperties egressStorageProperties) {
        this.egressStorageProperties = egressStorageProperties;
    }

    public boolean isConfigured() {
        return egressStorageProperties.isConfigured();
    }

    public String presignGetUrl(String objectKey, int expirySeconds) {
        if (!egressStorageProperties.isConfigured()) {
            throw new StorageOperationException(
                    "Egress storage (VB_EGRESS_*) is not configured",
                    new IllegalStateException("missing VB_EGRESS_*")
            );
        }
        try {
            return buildClient().getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(egressStorageProperties.bucket())
                            .object(objectKey)
                            .expiry(expirySeconds)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageOperationException("Egress playback presign failed: " + e.getMessage(), e);
        }
    }

    private MinioClient buildClient() {
        MinioClient.Builder builder = MinioClient.builder()
                .endpoint(egressStorageProperties.endpoint())
                .credentials(egressStorageProperties.accessKey(), egressStorageProperties.secretKey());
        String region = egressStorageProperties.region();
        if (region != null && !region.isBlank() && !"auto".equalsIgnoreCase(region)) {
            builder.region(region);
        }
        return builder.build();
    }
}
