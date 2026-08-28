package com.example.virtualbranch.chat;

import com.example.virtualbranch.chat.dto.ChatCollabPayload;
import com.example.virtualbranch.chat.dto.ChatDocumentPayload;
import com.example.virtualbranch.chat.dto.ChatHistoryResponse;
import com.example.virtualbranch.chat.dto.ChatMessageResponse;
import com.example.virtualbranch.chat.dto.ChatSettingsResponse;
import com.example.virtualbranch.collab.DocCollabEntity;
import com.example.virtualbranch.collab.DocCollabRepository;
import com.example.virtualbranch.collab.DocCollabService;
import com.example.virtualbranch.collab.DocCollabStatus;
import com.example.virtualbranch.collab.dto.DocCollabResponse;
import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.config.ChatProperties;
import com.example.virtualbranch.document.DocumentEntity;
import com.example.virtualbranch.document.DocumentRepository;
import com.example.virtualbranch.livekit.ParticipantRole;
import com.example.virtualbranch.session.SessionEntity;
import com.example.virtualbranch.session.SessionRepository;
import com.example.virtualbranch.session.SessionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.WebSocketSession;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 200;
    private static final int MAX_WS_FRAME_BYTES = 8192;

    private final SessionRepository sessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final DocumentRepository documentRepository;
    private final DocCollabRepository docCollabRepository;
    private final DocCollabService docCollabService;
    private final ChatFileValidator chatFileValidator;
    private final ChatProperties chatProperties;
    private final SessionChatHub sessionChatHub;
    private final ChatRateLimiter chatRateLimiter;

    public ChatService(
            SessionRepository sessionRepository,
            ChatMessageRepository chatMessageRepository,
            DocumentRepository documentRepository,
            DocCollabRepository docCollabRepository,
            @Lazy DocCollabService docCollabService,
            ChatFileValidator chatFileValidator,
            ChatProperties chatProperties,
            SessionChatHub sessionChatHub,
            ChatRateLimiter chatRateLimiter
    ) {
        this.sessionRepository = sessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.documentRepository = documentRepository;
        this.docCollabRepository = docCollabRepository;
        this.docCollabService = docCollabService;
        this.chatFileValidator = chatFileValidator;
        this.chatProperties = chatProperties;
        this.sessionChatHub = sessionChatHub;
        this.chatRateLimiter = chatRateLimiter;
    }

    public ChatSettingsResponse getSettings() {
        return new ChatSettingsResponse(
                chatProperties.maxFileSizeBytes(),
                chatProperties.maxFileSizeLabel(),
                List.copyOf(chatProperties.allowedContentTypes()),
                List.copyOf(chatProperties.allowedExtensions()),
                chatProperties.allowedExtensionsLabel()
        );
    }

    @Transactional(readOnly = true)
    public ChatHistoryResponse getHistory(String sessionId, String after, int limit) {
        requireSessionExists(sessionId);
        int pageSize = Math.min(Math.max(limit, 1), MAX_LIMIT) + 1;

        List<ChatMessageEntity> rows;
        if (after == null || after.isBlank()) {
            rows = chatMessageRepository.findHistory(
                    sessionId,
                    PageRequest.of(0, pageSize, Sort.by("sentAt").ascending().and(Sort.by("id").ascending()))
            );
        } else {
            ChatMessageEntity cursor = resolveCursor(sessionId, after);
            if (cursor == null) {
                rows = chatMessageRepository.findHistory(
                        sessionId,
                        PageRequest.of(0, pageSize, Sort.by("sentAt").ascending().and(Sort.by("id").ascending()))
                );
            } else {
                rows = chatMessageRepository.findAfter(
                        sessionId,
                        cursor.getSentAt(),
                        cursor.getId(),
                        PageRequest.of(0, pageSize)
                );
            }
        }

        boolean hasMore = rows.size() > Math.min(Math.max(limit, 1), MAX_LIMIT);
        if (hasMore) {
            rows = new ArrayList<>(rows.subList(0, Math.min(Math.max(limit, 1), MAX_LIMIT)));
        }

        List<ChatMessageResponse> messages = rows.stream().map(this::toResponse).toList();
        return new ChatHistoryResponse(sessionId, messages, hasMore);
    }

    @Transactional
    public void handleChatSend(
            String sessionId,
            ChatParticipant participant,
            WebSocketSession socketSession,
            JsonNode root
    ) {
        requireActiveSession(sessionId);
        String clientMessageId = textOrNull(root, "clientMessageId");
        JsonNode payload = root.get("payload");
        if (payload == null || payload.isNull()) {
            sendError(socketSession, clientMessageId, "INVALID_REQUEST", "Missing payload");
            return;
        }

        String raw = root.toString();
        if (raw.length() > MAX_WS_FRAME_BYTES) {
            sendError(socketSession, clientMessageId, "INVALID_REQUEST", "Message too large");
            return;
        }

        if (!chatRateLimiter.allow(sessionId, participant.identity())) {
            sendError(socketSession, clientMessageId, "INVALID_REQUEST", "Rate limit exceeded");
            return;
        }

        String messageTypeRaw = textOrNull(payload, "messageType");
        ChatMessageType messageType;
        try {
            messageType = ChatMessageType.valueOf(messageTypeRaw);
        } catch (Exception exception) {
            sendError(socketSession, clientMessageId, "INVALID_REQUEST", "Invalid messageType");
            return;
        }

        try {
            switch (messageType) {
                case TEXT -> handleText(sessionId, participant, clientMessageId, payload);
                case FILE -> handleFile(sessionId, participant, clientMessageId, payload);
                case COLLAB_REQUEST -> handleCollabRequest(sessionId, participant, clientMessageId, payload);
                case COLLAB_CANCEL -> handleCollabCancel(sessionId, participant, clientMessageId, payload);
                default -> sendError(socketSession, clientMessageId, "INVALID_REQUEST", "Unsupported messageType");
            }
        } catch (ChatValidationException exception) {
            sendError(socketSession, clientMessageId, exception.errorCode(), exception.getMessage());
        } catch (BusinessException exception) {
            sendError(socketSession, clientMessageId, exception.getErrorCode().getCode(), exception.getMessage());
        } catch (Exception exception) {
            log.error("Chat send failed sessionId={}", sessionId, exception);
            sendError(socketSession, clientMessageId, "INTERNAL_ERROR", "Failed to process message");
        }
    }

    @Transactional
    public void publishCollabStatus(DocCollabEntity collab) {
        DocumentEntity document = documentRepository.findById(collab.getDocumentId()).orElse(null);
        ChatMessageEntity entity = new ChatMessageEntity(
                "MSG-" + UUID.randomUUID(),
                collab.getSessionId(),
                ParticipantRole.AGENT,
                "system",
                "Hệ thống",
                ChatMessageType.COLLAB_STATUS,
                null,
                collab.getDocumentId(),
                collab.getId(),
                collab.getStatus().name(),
                null,
                OffsetDateTime.now()
        );
        chatMessageRepository.save(entity);
        broadcastMessage(entity, document, collab.getId(), collab.getStatus().name());
    }

    private void handleText(String sessionId, ChatParticipant participant, String clientMessageId, JsonNode payload) {
        String text = textOrNull(payload, "text");
        if (text == null || text.isBlank()) {
            throw new ChatValidationException("INVALID_REQUEST");
        }
        if (text.length() > chatProperties.maxTextLength()) {
            throw new ChatValidationException("INVALID_REQUEST");
        }
        persistAndBroadcast(
                sessionId,
                participant,
                ChatMessageType.TEXT,
                text,
                null,
                null,
                null,
                clientMessageId
        );
    }

    private void handleFile(String sessionId, ChatParticipant participant, String clientMessageId, JsonNode payload) {
        String documentId = textOrNull(payload, "documentId");
        if (documentId == null || documentId.isBlank()) {
            throw new ChatValidationException("INVALID_REQUEST");
        }
        DocumentEntity document = requireDocumentForSession(documentId, sessionId);
        if (document.getFileSize() > chatProperties.maxFileSizeBytes()) {
            throw new ChatValidationException("FILE_TOO_LARGE");
        }
        persistAndBroadcast(
                sessionId,
                participant,
                ChatMessageType.FILE,
                null,
                document,
                null,
                null,
                clientMessageId
        );
    }

    private void handleCollabRequest(
            String sessionId,
            ChatParticipant participant,
            String clientMessageId,
            JsonNode payload
    ) {
        if (participant.role() != ParticipantRole.AGENT) {
            throw new ChatValidationException("FORBIDDEN");
        }
        String documentId = textOrNull(payload, "documentId");
        if (documentId == null || documentId.isBlank()) {
            throw new ChatValidationException("INVALID_REQUEST");
        }
        DocumentEntity document = requireDocumentForSession(documentId, sessionId);
        if (!chatFileValidator.isCollabEligible(document)) {
            throw new ChatValidationException("FILE_TYPE_NOT_ALLOWED");
        }

        DocCollabResponse started = docCollabService.startCollab(sessionId, documentId);
        persistAndBroadcast(
                sessionId,
                participant,
                ChatMessageType.COLLAB_REQUEST,
                null,
                document,
                started.collabId(),
                DocCollabStatus.REQUESTED.name(),
                clientMessageId
        );
    }

    private void handleCollabCancel(
            String sessionId,
            ChatParticipant participant,
            String clientMessageId,
            JsonNode payload
    ) {
        if (participant.role() != ParticipantRole.AGENT) {
            throw new ChatValidationException("FORBIDDEN");
        }
        String collabId = textOrNull(payload, "collabId");
        if (collabId == null || collabId.isBlank()) {
            throw new ChatValidationException("INVALID_REQUEST");
        }
        DocCollabEntity collab = docCollabRepository.findById(collabId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COLLAB_NOT_FOUND));
        if (!collab.getSessionId().equals(sessionId)) {
            throw new BusinessException(ErrorCode.COLLAB_NOT_FOUND);
        }
        if (collab.getStatus() == DocCollabStatus.REQUESTED) {
            docCollabService.endCollab(collabId, "AGENT_CANCELLED");
        }
        DocumentEntity document = documentRepository.findById(collab.getDocumentId()).orElse(null);
        persistAndBroadcast(
                sessionId,
                participant,
                ChatMessageType.COLLAB_CANCEL,
                null,
                document,
                collabId,
                DocCollabStatus.ENDED.name(),
                clientMessageId
        );
    }

    private void persistAndBroadcast(
            String sessionId,
            ChatParticipant participant,
            ChatMessageType messageType,
            String text,
            DocumentEntity document,
            String collabId,
            String collabStatus,
            String clientMessageId
    ) {
        ChatMessageEntity entity = new ChatMessageEntity(
                "MSG-" + UUID.randomUUID(),
                sessionId,
                participant.role(),
                participant.identity(),
                participant.name(),
                messageType,
                text,
                document != null ? document.getId() : null,
                collabId,
                collabStatus,
                clientMessageId,
                OffsetDateTime.now()
        );
        chatMessageRepository.save(entity);
        broadcastMessage(entity, document, collabId, collabStatus);
    }

    private void broadcastMessage(
            ChatMessageEntity entity,
            DocumentEntity document,
            String collabId,
            String collabStatus
    ) {
        sessionChatHub.broadcast(entity.getSessionId(), toWsEnvelope(entity, document, collabId, collabStatus));
    }

    private Map<String, Object> toWsEnvelope(
            ChatMessageEntity entity,
            DocumentEntity document,
            String collabId,
            String collabStatus
    ) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("version", 1);
        envelope.put("type", "CHAT_MESSAGE");
        envelope.put("messageId", entity.getId());
        envelope.put("sessionId", entity.getSessionId());
        envelope.put("sentAt", entity.getSentAt().toString());
        envelope.put("senderRole", entity.getSenderRole().name());
        envelope.put("senderIdentity", entity.getSenderIdentity());
        envelope.put("senderName", entity.getSenderName());
        envelope.put("messageType", entity.getMessageType().name());
        if (entity.getClientMessageId() != null) {
            envelope.put("clientMessageId", entity.getClientMessageId());
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        switch (entity.getMessageType()) {
            case TEXT -> payload.put("text", entity.getTextBody());
            case FILE -> {
                if (document != null) {
                    payload.put("documentId", document.getId());
                    payload.put("fileName", document.getFileName());
                    payload.put("contentType", document.getContentType());
                    payload.put("sizeBytes", document.getFileSize());
                }
            }
            case COLLAB_REQUEST, COLLAB_STATUS, COLLAB_CANCEL -> {
                if (collabId != null) {
                    payload.put("collabId", collabId);
                }
                if (collabStatus != null) {
                    payload.put("collabStatus", collabStatus);
                }
                if (document != null) {
                    payload.put("documentId", document.getId());
                    payload.put("fileName", document.getFileName());
                    payload.put("contentType", document.getContentType());
                    payload.put("sizeBytes", document.getFileSize());
                } else if (entity.getDocumentId() != null) {
                    payload.put("documentId", entity.getDocumentId());
                }
            }
            default -> {
                // no-op
            }
        }
        envelope.put("payload", payload);
        return envelope;
    }

    private ChatMessageResponse toResponse(ChatMessageEntity entity) {
        DocumentEntity document = entity.getDocumentId() != null
                ? documentRepository.findById(entity.getDocumentId()).orElse(null)
                : null;
        ChatDocumentPayload documentPayload = document != null
                ? new ChatDocumentPayload(
                        document.getId(),
                        document.getFileName(),
                        document.getContentType(),
                        document.getFileSize()
                )
                : null;
        ChatCollabPayload collabPayload = entity.getCollabId() != null
                ? new ChatCollabPayload(
                        entity.getCollabId(),
                        entity.getCollabStatus(),
                        entity.getDocumentId()
                )
                : null;
        return new ChatMessageResponse(
                entity.getId(),
                entity.getSentAt(),
                entity.getSenderRole(),
                entity.getSenderIdentity(),
                entity.getSenderName(),
                entity.getMessageType(),
                entity.getTextBody(),
                documentPayload,
                collabPayload,
                entity.getClientMessageId()
        );
    }

    private ChatMessageEntity resolveCursor(String sessionId, String after) {
        ChatMessageEntity byId = chatMessageRepository.findById(after).orElse(null);
        if (byId != null && byId.getSessionId().equals(sessionId)) {
            return byId;
        }
        try {
            OffsetDateTime timestamp = OffsetDateTime.parse(after);
            List<ChatMessageEntity> rows = chatMessageRepository.findHistory(
                    sessionId,
                    PageRequest.of(0, 1, Sort.by("sentAt").descending())
            );
            for (ChatMessageEntity row : rows) {
                if (!row.getSentAt().isAfter(timestamp)) {
                    return row;
                }
            }
        } catch (DateTimeParseException ignored) {
            // not a timestamp
        }
        return null;
    }

    private DocumentEntity requireDocumentForSession(String documentId, String sessionId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND));
        if (!document.getSessionId().equals(sessionId)) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    private SessionEntity requireSessionExists(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        ErrorCode.SESSION_NOT_FOUND.getDefaultMessage(),
                        HttpStatus.NOT_FOUND
                ));
    }

    private SessionEntity requireActiveSession(String sessionId) {
        SessionEntity session = requireSessionExists(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CALL_NOT_ACCEPTED);
        }
        return session;
    }

    public void validateHandshake(String sessionId, ChatParticipant participant) {
        SessionEntity session = requireSessionExists(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }
        if (session.getStatus() != SessionStatus.ACTIVE) {
            throw new BusinessException(ErrorCode.CALL_NOT_ACCEPTED);
        }
        if (participant.role() == ParticipantRole.AGENT
                && session.getAgentIdentity() != null
                && !session.getAgentIdentity().equals(participant.identity())) {
            throw new BusinessException(ErrorCode.INVALID_ROLE);
        }
        if (participant.role() == ParticipantRole.CUSTOMER
                && session.getCustomerIdentity() != null
                && !session.getCustomerIdentity().equals(participant.identity())) {
            throw new BusinessException(ErrorCode.INVALID_ROLE);
        }
    }

    public void sendError(WebSocketSession session, String clientMessageId, String code, String message) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("version", 1);
        envelope.put("type", "CHAT_ERROR");
        envelope.put("code", code);
        envelope.put("message", message);
        if (clientMessageId != null) {
            envelope.put("clientMessageId", clientMessageId);
        }
        sessionChatHub.sendTo(session, envelope);
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
