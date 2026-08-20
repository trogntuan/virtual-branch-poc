package com.example.virtualbranch.livekit;

public enum ParticipantRole {
    AGENT,
    CUSTOMER;

    public static ParticipantRole from(String value) {
        if (value == null) {
            return null;
        }
        try {
            return ParticipantRole.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
