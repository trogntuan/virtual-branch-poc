package com.example.virtualbranch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "virtual-branch.livekit")
public record LiveKitProperties(
        String apiUrl,
        String wsUrl,
        String apiKey,
        String apiSecret
) {
}
