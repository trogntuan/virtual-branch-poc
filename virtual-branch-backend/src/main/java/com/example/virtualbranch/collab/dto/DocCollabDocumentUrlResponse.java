package com.example.virtualbranch.collab.dto;

public record DocCollabDocumentUrlResponse(
        String documentId,
        String readUrl,
        int expiresInSeconds
) {
}
