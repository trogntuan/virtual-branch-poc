package com.example.virtualbranch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "virtual-branch.storage")
public record StorageProperties(
        String provider,
        String endpoint,
        String bucket,
        String accessKey,
        String secretKey,
        String region,
        boolean forcePathStyle,
        String gcsCredentialsPath,
        String gcsCredentialsJson
) {
    public StorageProperties {
        if (provider == null || provider.isBlank()) {
            provider = "s3";
        }
        if (region == null) {
            region = "";
        }
    }

    public boolean isGcs() {
        return "gcs".equalsIgnoreCase(provider);
    }
}
