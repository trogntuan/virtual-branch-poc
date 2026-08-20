package com.example.virtualbranch.session.dto;

import jakarta.validation.constraints.NotBlank;

public record AcceptCallRequest(
        @NotBlank String identity,
        @NotBlank String name
) {
}
