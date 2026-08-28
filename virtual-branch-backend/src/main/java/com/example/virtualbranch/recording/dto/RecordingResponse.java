package com.example.virtualbranch.recording.dto;

import com.example.virtualbranch.recording.RecordingStatus;
import java.util.List;

public record RecordingResponse(
        String recordingId,
        String sessionId,
        String egressId,
        RecordingStatus status,
        String objectKey,
        String playbackUrl,
        String errorMessage,
        String mode,
        String groupId,
        List<RecordingTrackResponse> tracks
) {
}
