package com.example.virtualbranch.settings.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateRecordingModeSettingRequest(
        @NotBlank String mode
) {
}
