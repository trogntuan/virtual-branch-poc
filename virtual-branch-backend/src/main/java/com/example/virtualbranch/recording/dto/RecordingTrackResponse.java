package com.example.virtualbranch.recording.dto;

import com.example.virtualbranch.recording.RecordingStatus;

public record RecordingTrackResponse(
        String recordingId,
        String side,
        String egressId,
        RecordingStatus status,
        String objectKey,
        String playbackUrl,
        String errorMessage
) {
}
