package com.example.virtualbranch.chat;

import com.example.virtualbranch.livekit.ParticipantRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vb_chat_message")
public class ChatMessageEntity {

    @Id
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_role", nullable = false)
    private ParticipantRole senderRole;

    @Column(name = "sender_identity", nullable = false)
    private String senderIdentity;

    @Column(name = "sender_name")
    private String senderName;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", nullable = false)
    private ChatMessageType messageType;

    @Column(name = "text_body")
    private String textBody;

    @Column(name = "document_id")
    private String documentId;

    @Column(name = "collab_id")
    private String collabId;

    @Column(name = "collab_status")
    private String collabStatus;

    @Column(name = "client_message_id")
    private String clientMessageId;

    @Column(name = "sent_at", nullable = false)
    private OffsetDateTime sentAt;

    protected ChatMessageEntity() {
    }

    public ChatMessageEntity(
            String id,
            String sessionId,
            ParticipantRole senderRole,
            String senderIdentity,
            String senderName,
            ChatMessageType messageType,
            String textBody,
            String documentId,
            String collabId,
            String collabStatus,
            String clientMessageId,
            OffsetDateTime sentAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.senderRole = senderRole;
        this.senderIdentity = senderIdentity;
        this.senderName = senderName;
        this.messageType = messageType;
        this.textBody = textBody;
        this.documentId = documentId;
        this.collabId = collabId;
        this.collabStatus = collabStatus;
        this.clientMessageId = clientMessageId;
        this.sentAt = sentAt;
    }

    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public ParticipantRole getSenderRole() {
        return senderRole;
    }

    public String getSenderIdentity() {
        return senderIdentity;
    }

    public String getSenderName() {
        return senderName;
    }

    public ChatMessageType getMessageType() {
        return messageType;
    }

    public String getTextBody() {
        return textBody;
    }

    public String getDocumentId() {
        return documentId;
    }

    public String getCollabId() {
        return collabId;
    }

    public String getCollabStatus() {
        return collabStatus;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public OffsetDateTime getSentAt() {
        return sentAt;
    }
}
