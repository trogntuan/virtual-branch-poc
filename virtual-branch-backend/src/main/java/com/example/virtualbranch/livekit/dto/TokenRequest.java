package com.example.virtualbranch.livekit.dto;

import com.example.virtualbranch.livekit.ParticipantRole;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record TokenRequest(
        @NotBlank String identity,
        String name,
        @NotNull ParticipantRole role
) {
}
