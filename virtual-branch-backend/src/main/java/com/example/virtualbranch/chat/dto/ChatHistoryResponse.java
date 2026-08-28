package com.example.virtualbranch.chat.dto;

import java.util.List;

public record ChatHistoryResponse(
        String sessionId,
        List<ChatMessageResponse> messages,
        boolean hasMore
) {
}
