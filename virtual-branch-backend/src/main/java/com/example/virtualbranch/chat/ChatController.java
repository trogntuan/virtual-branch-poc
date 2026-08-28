package com.example.virtualbranch.chat;

import com.example.virtualbranch.chat.dto.ChatHistoryResponse;
import com.example.virtualbranch.chat.dto.ChatSettingsResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @GetMapping("/chat/settings")
    public ChatSettingsResponse getSettings() {
        return chatService.getSettings();
    }

    @GetMapping("/sessions/{sessionId}/chat/messages")
    public ChatHistoryResponse getMessages(
            @PathVariable String sessionId,
            @RequestParam(required = false) String after,
            @RequestParam(defaultValue = "50") int limit
    ) {
        return chatService.getHistory(sessionId, after, limit);
    }
}
