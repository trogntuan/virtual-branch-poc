package com.example.virtualbranch.chat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

@Component
public class SessionChatHub {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, Set<WebSocketSession>> sessionsByRoom = new ConcurrentHashMap<>();

    public void register(String sessionId, WebSocketSession session) {
        sessionsByRoom.computeIfAbsent(sessionId, ignored -> new CopyOnWriteArraySet<>()).add(session);
    }

    public void unregister(String sessionId, WebSocketSession session) {
        Set<WebSocketSession> sessions = sessionsByRoom.get(sessionId);
        if (sessions == null) {
            return;
        }
        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByRoom.remove(sessionId, sessions);
        }
    }

    public void broadcast(String sessionId, Map<String, Object> envelope) {
        Set<WebSocketSession> sessions = sessionsByRoom.get(sessionId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }
        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (IOException exception) {
            return;
        }
        TextMessage message = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException ignored) {
                    // drop failed session
                }
            }
        }
    }

    public void sendTo(WebSocketSession session, Map<String, Object> envelope) {
        if (!session.isOpen()) {
            return;
        }
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(envelope)));
        } catch (IOException ignored) {
            // ignore
        }
    }
}
