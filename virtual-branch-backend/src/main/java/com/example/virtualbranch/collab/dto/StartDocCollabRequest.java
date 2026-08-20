package com.example.virtualbranch.collab.dto;

import jakarta.validation.constraints.NotNull;

public record StartDocCollabRequest(
        @NotNull String documentId
) {
}
