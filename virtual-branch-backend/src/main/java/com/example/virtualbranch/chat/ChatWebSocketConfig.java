package com.example.virtualbranch.chat;

import com.example.virtualbranch.config.CorsProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class ChatWebSocketConfig implements WebSocketConfigurer {

    private final SessionChatWebSocketHandler sessionChatWebSocketHandler;
    private final ChatHandshakeInterceptor chatHandshakeInterceptor;
    private final CorsProperties corsProperties;

    public ChatWebSocketConfig(
            SessionChatWebSocketHandler sessionChatWebSocketHandler,
            ChatHandshakeInterceptor chatHandshakeInterceptor,
            CorsProperties corsProperties
    ) {
        this.sessionChatWebSocketHandler = sessionChatWebSocketHandler;
        this.chatHandshakeInterceptor = chatHandshakeInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(sessionChatWebSocketHandler, "/api/v1/ws/sessions/{sessionId}/chat")
                .addInterceptors(chatHandshakeInterceptor)
                .setAllowedOriginPatterns(corsProperties.allowedOriginsArray());
    }
}
