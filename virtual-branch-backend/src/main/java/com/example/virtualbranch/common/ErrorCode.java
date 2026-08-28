package com.example.virtualbranch.common;

public enum ErrorCode {
    SESSION_NOT_FOUND("SESSION_NOT_FOUND", "Session not found"),
    SESSION_ENDED("SESSION_ENDED", "Session has ended"),
    CALL_NOT_WAITING("CALL_NOT_WAITING", "Call is not in waiting state"),
    CALL_NOT_ACCEPTED("CALL_NOT_ACCEPTED", "Call has not been accepted by an agent yet"),
    INVALID_ROLE("INVALID_ROLE", "Invalid participant role"),
    TOKEN_GENERATION_FAILED("TOKEN_GENERATION_FAILED", "Failed to generate LiveKit token"),
    RECORDING_NOT_FOUND("RECORDING_NOT_FOUND", "Recording not found"),
    RECORDING_START_FAILED("RECORDING_START_FAILED", "Failed to start recording"),
    RECORDING_STOP_FAILED("RECORDING_STOP_FAILED", "Failed to stop recording"),
    DOCUMENT_NOT_FOUND("DOCUMENT_NOT_FOUND", "Document not found"),
    INVALID_DOCUMENT("INVALID_DOCUMENT", "Invalid document"),
    FILE_TOO_LARGE("FILE_TOO_LARGE", "File exceeds maximum size for chat"),
    FILE_TYPE_NOT_ALLOWED("FILE_TYPE_NOT_ALLOWED", "File type is not allowed for chat"),
    FORBIDDEN("FORBIDDEN", "Action not allowed for this role"),
    STORAGE_UPLOAD_FAILED("STORAGE_UPLOAD_FAILED", "Failed to upload to storage"),
    COLLAB_NOT_FOUND("COLLAB_NOT_FOUND", "Doc collab not found"),
    COLLAB_INVALID_STATE("COLLAB_INVALID_STATE", "Doc collab is not in a valid state for this action"),
    COLLAB_CONSENT_REQUIRED("COLLAB_CONSENT_REQUIRED", "Customer consent is required before document access"),
    INVALID_CONSENT("INVALID_CONSENT", "Invalid consent decision"),
    INVALID_REQUEST("INVALID_REQUEST", "Invalid request"),
    INTERNAL_ERROR("INTERNAL_ERROR", "An unexpected error occurred");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String getCode() {
        return code;
    }

    public String getDefaultMessage() {
        return defaultMessage;
    }
}
