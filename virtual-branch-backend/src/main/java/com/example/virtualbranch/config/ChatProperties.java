package com.example.virtualbranch.config;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "virtual-branch.chat")
public class ChatProperties {

    private long maxFileSizeBytes = 3L * 1024 * 1024;
    private String allowedContentTypes =
            "application/pdf,application/msword,application/vnd.ms-excel,"
                    + "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,"
                    + "image/jpeg,image/png,image/gif,image/webp";
    private String allowedExtensions = "pdf,doc,xls,xlsx,jpg,jpeg,png,gif,webp";
    private String collabAllowedContentTypes = "application/pdf";
    private String collabAllowedExtensions = "pdf";
    private int maxTextLength = 4000;

    public long maxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public void setMaxFileSizeBytes(long maxFileSizeBytes) {
        this.maxFileSizeBytes = maxFileSizeBytes;
    }

    public int maxTextLength() {
        return maxTextLength;
    }

    public void setMaxTextLength(int maxTextLength) {
        this.maxTextLength = maxTextLength;
    }

    public Set<String> allowedContentTypes() {
        return splitCsv(allowedContentTypes);
    }

    public void setAllowedContentTypes(String allowedContentTypes) {
        this.allowedContentTypes = allowedContentTypes;
    }

    public Set<String> allowedExtensions() {
        return splitCsv(allowedExtensions);
    }

    public void setAllowedExtensions(String allowedExtensions) {
        this.allowedExtensions = allowedExtensions;
    }

    public Set<String> collabAllowedContentTypes() {
        return splitCsv(collabAllowedContentTypes);
    }

    public void setCollabAllowedContentTypes(String collabAllowedContentTypes) {
        this.collabAllowedContentTypes = collabAllowedContentTypes;
    }

    public Set<String> collabAllowedExtensions() {
        return splitCsv(collabAllowedExtensions);
    }

    public void setCollabAllowedExtensions(String collabAllowedExtensions) {
        this.collabAllowedExtensions = collabAllowedExtensions;
    }

    public String maxFileSizeLabel() {
        if (maxFileSizeBytes >= 1024 * 1024) {
            return (maxFileSizeBytes / (1024 * 1024)) + " MB";
        }
        return (maxFileSizeBytes / 1024) + " KB";
    }

    public String allowedExtensionsLabel() {
        return allowedExtensions().stream()
                .map(ext -> switch (ext) {
                    case "pdf" -> "PDF";
                    case "doc" -> "DOC";
                    case "xls", "xlsx" -> "Excel";
                    case "jpg", "jpeg", "png", "gif", "webp" -> "ảnh";
                    default -> ext.toUpperCase(Locale.ROOT);
                })
                .distinct()
                .collect(Collectors.joining(", "));
    }

    private static Set<String> splitCsv(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
