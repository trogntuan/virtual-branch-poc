package com.example.virtualbranch.chat.dto;

public record ChatDocumentPayload(
        String documentId,
        String fileName,
        String contentType,
        long sizeBytes
) {
}
