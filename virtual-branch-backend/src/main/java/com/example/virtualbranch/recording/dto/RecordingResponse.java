package com.example.virtualbranch.recording.dto;

import com.example.virtualbranch.recording.RecordingStatus;

public record RecordingResponse(
        String recordingId,
        String sessionId,
        String egressId,
        RecordingStatus status,
        String objectKey,
        String playbackUrl,
        String errorMessage
) {
}

