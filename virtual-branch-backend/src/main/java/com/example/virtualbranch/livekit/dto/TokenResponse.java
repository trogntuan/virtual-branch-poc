package com.example.virtualbranch.livekit.dto;

public record TokenResponse(
        String serverUrl,
        String roomName,
        String participantToken
) {
}
