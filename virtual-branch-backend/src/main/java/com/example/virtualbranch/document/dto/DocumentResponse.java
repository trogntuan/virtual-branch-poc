package com.example.virtualbranch.document.dto;

public record DocumentResponse(
        String documentId,
        String fileName,
        String contentType,
        long size,
        String readUrl
) {
}
