package com.example.virtualbranch.common;

import java.time.OffsetDateTime;

public record ApiError(
        String code,
        String message,
        OffsetDateTime timestamp
) {
    public static ApiError of(ErrorCode errorCode, String message) {
        return new ApiError(errorCode.getCode(), message, OffsetDateTime.now());
    }
}
