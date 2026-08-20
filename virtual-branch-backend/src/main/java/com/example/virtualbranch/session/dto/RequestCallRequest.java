package com.example.virtualbranch.session.dto;

import com.example.virtualbranch.session.MobileOrientation;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RequestCallRequest(
        @NotBlank String identity,
        @NotBlank String name,
        @NotNull @Positive Integer viewportWidth,
        @NotNull @Positive Integer viewportHeight,
        @NotNull @Positive Double devicePixelRatio,
        @NotBlank String orientation
) {
    public MobileOrientation parsedOrientation() {
        return MobileOrientation.from(orientation);
    }
}
