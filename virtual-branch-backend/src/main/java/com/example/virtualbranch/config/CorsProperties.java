package com.example.virtualbranch.config;

import java.util.Arrays;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "virtual-branch.cors")
public record CorsProperties(
        String allowedOrigins
) {
    public CorsProperties {
        if (allowedOrigins == null || allowedOrigins.isBlank()) {
            allowedOrigins = "http://localhost:5173,http://127.0.0.1:5173";
        }
    }

    public String[] allowedOriginsArray() {
        return Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);
    }
}
