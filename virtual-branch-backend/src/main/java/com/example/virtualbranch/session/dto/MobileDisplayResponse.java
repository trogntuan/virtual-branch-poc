package com.example.virtualbranch.session.dto;

public record MobileDisplayResponse(
        Integer viewportWidth,
        Integer viewportHeight,
        Double devicePixelRatio,
        String orientation
) {
}
