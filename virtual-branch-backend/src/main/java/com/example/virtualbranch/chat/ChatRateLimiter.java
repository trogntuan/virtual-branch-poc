package com.example.virtualbranch.chat;

import com.example.virtualbranch.livekit.ParticipantRole;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class ChatRateLimiter {

    private static final int MAX_MESSAGES_PER_MINUTE = 30;
    private static final long WINDOW_MS = 60_000L;

    private final ConcurrentHashMap<String, Deque<Long>> buckets = new ConcurrentHashMap<>();

    public boolean allow(String sessionId, String identity) {
        String key = sessionId + ":" + identity;
        long now = System.currentTimeMillis();
        Deque<Long> window = buckets.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (window) {
            while (!window.isEmpty() && now - window.peekFirst() > WINDOW_MS) {
                window.pollFirst();
            }
            if (window.size() >= MAX_MESSAGES_PER_MINUTE) {
                return false;
            }
            window.addLast(now);
            return true;
        }
    }
}
