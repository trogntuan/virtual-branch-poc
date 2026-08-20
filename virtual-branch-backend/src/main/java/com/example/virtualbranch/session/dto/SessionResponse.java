package com.example.virtualbranch.session.dto;

import com.example.virtualbranch.session.SessionStatus;
import java.time.OffsetDateTime;

public record SessionResponse(
        String sessionId,
        String roomName,
        SessionStatus status,
        OffsetDateTime createdAt,
        OffsetDateTime endedAt,
        String customerIdentity,
        String customerName,
        String agentIdentity,
        String agentName,
        OffsetDateTime acceptedAt
) {
}
