package com.example.virtualbranch.document.dto;

public record DocumentUrlResponse(
        String documentId,
        String readUrl,
        int expiresInSeconds
) {
}
