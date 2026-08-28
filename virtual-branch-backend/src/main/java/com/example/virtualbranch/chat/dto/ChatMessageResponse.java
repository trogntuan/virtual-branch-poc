package com.example.virtualbranch.chat.dto;

import com.example.virtualbranch.chat.ChatMessageType;
import com.example.virtualbranch.livekit.ParticipantRole;
import java.time.OffsetDateTime;

public record ChatMessageResponse(
        String messageId,
        OffsetDateTime sentAt,
        ParticipantRole senderRole,
        String senderIdentity,
        String senderName,
        ChatMessageType messageType,
        String text,
        ChatDocumentPayload document,
        ChatCollabPayload collab,
        String clientMessageId
) {
}
