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
                .endpoint(browserReachableEndpoint())
                .credentials(egressStorageProperties.accessKey(), egressStorageProperties.secretKey());
        String region = egressStorageProperties.region();
        if (region != null && !region.isBlank() && !"auto".equalsIgnoreCase(region)) {
            builder.region(region);
        }
        return builder.build();
    }

    /**
     * Egress containers use docker DNS ({@code minio:9000}); browsers need localhost.
     */
    private String browserReachableEndpoint() {
        String endpoint = egressStorageProperties.endpoint();
        if (endpoint == null || endpoint.isBlank()) {
            return endpoint;
        }
        String lower = endpoint.toLowerCase();
        if (lower.contains("://minio:") || lower.contains("://minio/") || lower.contains("host.docker.internal")) {
            return "http://localhost:9000";
        }
        return endpoint;
    }
}
