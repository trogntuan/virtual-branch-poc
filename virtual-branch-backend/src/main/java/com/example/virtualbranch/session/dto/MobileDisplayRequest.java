package com.example.virtualbranch.session.dto;

import com.example.virtualbranch.session.MobileOrientation;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MobileDisplayRequest(
        @NotNull @Positive Integer viewportWidth,
        @NotNull @Positive Integer viewportHeight,
        @NotNull @Positive Double devicePixelRatio,
        @NotNull String orientation
) {
    public MobileOrientation parsedOrientation() {
        return MobileOrientation.from(orientation);
    }
}
