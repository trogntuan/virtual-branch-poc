package com.example.virtualbranch.chat;

import java.io.IOException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class SessionChatWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SessionChatWebSocketHandler.class);

    private final ChatService chatService;
    private final SessionChatHub sessionChatHub;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public SessionChatWebSocketHandler(
            ChatService chatService,
            SessionChatHub sessionChatHub
    ) {
        this.chatService = chatService;
        this.sessionChatHub = sessionChatHub;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String sessionId = (String) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_SESSION_ID);
        if (sessionId != null) {
            sessionChatHub.register(sessionId, session);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String sessionId = (String) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_SESSION_ID);
        ChatParticipant participant = (ChatParticipant) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_PARTICIPANT);
        if (sessionId == null || participant == null) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) {
                // ignore
            }
            return;
        }

        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            String type = root.path("type").asText();
            if (!"CHAT_SEND".equals(type)) {
                chatService.sendError(session, textOrNull(root, "clientMessageId"), "INVALID_REQUEST", "Unsupported type");
                return;
            }
            chatService.handleChatSend(sessionId, participant, session, root);
        } catch (JsonProcessingException exception) {
            chatService.sendError(session, null, "INVALID_REQUEST", "Invalid JSON");
        } catch (Exception exception) {
            log.warn("Chat websocket handler failed sessionId={}", sessionId, exception);
            chatService.sendError(session, null, "INTERNAL_ERROR", "Failed to process message");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String sessionId = (String) session.getAttributes().get(ChatHandshakeInterceptor.ATTR_SESSION_ID);
        if (sessionId != null) {
            sessionChatHub.unregister(sessionId, session);
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }
}
