package com.example.virtualbranch.chat.dto;

import java.util.List;

public record ChatSettingsResponse(
        long maxFileSizeBytes,
        String maxFileSizeLabel,
        List<String> allowedContentTypes,
        List<String> allowedExtensions,
        String allowedExtensionsLabel
) {
}
