package com.example.virtualbranch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "virtual-branch.egress-storage")
public record EgressStorageProperties(
        String bucket,
        String endpoint,
        String accessKey,
        String secretKey,
        String region,
        boolean forcePathStyle
) {
    public EgressStorageProperties {
        if (region == null) {
            region = "auto";
        }
    }

    public boolean isConfigured() {
        return bucket != null
                && !bucket.isBlank()
                && endpoint != null
                && !endpoint.isBlank()
                && accessKey != null
                && !accessKey.isBlank()
                && secretKey != null
                && !secretKey.isBlank();
    }
}
