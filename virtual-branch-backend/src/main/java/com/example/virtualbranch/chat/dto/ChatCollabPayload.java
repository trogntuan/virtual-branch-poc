package com.example.virtualbranch.chat.dto;

public record ChatCollabPayload(
        String collabId,
        String status,
        String documentId
) {
}
