package com.example.virtualbranch.chat;

import com.example.virtualbranch.livekit.ParticipantRole;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class ChatHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_SESSION_ID = "chat.sessionId";
    public static final String ATTR_PARTICIPANT = "chat.participant";

    private final ChatService chatService;

    public ChatHandshakeInterceptor(ChatService chatService) {
        this.chatService = chatService;
    }

    @Override
    public boolean beforeHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Map<String, Object> attributes
    ) {
        URI uri = request.getURI();
        String path = uri.getPath();
        String sessionId = extractSessionId(path);
        if (sessionId == null) {
            return false;
        }

        var query = UriComponentsBuilder.fromUri(uri).build().getQueryParams();
        String identity = decodeQueryParam(query.getFirst("identity"));
        String roleRaw = decodeQueryParam(query.getFirst("role"));
        String name = decodeQueryParam(query.getFirst("name"));

        ParticipantRole role = ParticipantRole.from(roleRaw);
        if (identity == null || role == null) {
            return false;
        }

        ChatParticipant participant = new ChatParticipant(identity, role, name != null ? name : identity);
        try {
            chatService.validateHandshake(sessionId, participant);
        } catch (Exception exception) {
            return false;
        }

        attributes.put(ATTR_SESSION_ID, sessionId);
        attributes.put(ATTR_PARTICIPANT, participant);
        return true;
    }

    @Override
    public void afterHandshake(
            ServerHttpRequest request,
            ServerHttpResponse response,
            WebSocketHandler wsHandler,
            Exception exception
    ) {
        // no-op
    }

    private static String extractSessionId(String path) {
        // /api/v1/ws/sessions/{sessionId}/chat
        String[] parts = path.split("/");
        for (int i = 0; i < parts.length - 1; i++) {
            if ("sessions".equals(parts[i]) && i + 1 < parts.length) {
                return parts[i + 1];
            }
        }
        return null;
    }

    private static String decodeQueryParam(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return URLDecoder.decode(value.trim(), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return value.trim();
        }
    }
}
