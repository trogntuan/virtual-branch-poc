package com.example.virtualbranch.collab.dto;

import com.example.virtualbranch.collab.ConsentDecision;
import com.example.virtualbranch.collab.DocCollabStatus;

public record DocCollabResponse(
        String collabId,
        String sessionId,
        String documentId,
        DocCollabStatus status,
        ConsentDecision consentDecision
) {
}
