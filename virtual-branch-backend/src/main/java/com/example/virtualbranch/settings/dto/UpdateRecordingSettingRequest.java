package com.example.virtualbranch.settings.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateRecordingSettingRequest(
        @NotNull Boolean enabled
) {
}
