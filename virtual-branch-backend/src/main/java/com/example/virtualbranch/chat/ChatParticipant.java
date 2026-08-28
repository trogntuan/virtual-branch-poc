package com.example.virtualbranch.chat;

import com.example.virtualbranch.livekit.ParticipantRole;

public record ChatParticipant(String identity, ParticipantRole role, String name) {
}
