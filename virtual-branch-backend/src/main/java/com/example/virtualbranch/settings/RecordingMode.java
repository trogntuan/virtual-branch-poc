package com.example.virtualbranch.settings;

public enum RecordingMode {
    /** One RoomComposite MP4 (grid, both sides merged). */
    ROOM_COMPOSITE,
    /** Two Participant egress MP4s (agent + customer, not merged). */
    DUAL_PARTICIPANT;

    public static RecordingMode fromSetting(String raw) {
        if (raw == null || raw.isBlank()) {
            return ROOM_COMPOSITE;
        }
        return switch (raw.trim().toUpperCase()) {
            case "DUAL_PARTICIPANT", "DUAL", "SEPARATE", "PARTICIPANT" -> DUAL_PARTICIPANT;
            case "ROOM_COMPOSITE", "COMPOSITE", "MERGED" -> ROOM_COMPOSITE;
            default -> null;
        };
    }

    public static RecordingMode requireFromSetting(String raw) {
        RecordingMode mode = fromSetting(raw);
        if (mode == null) {
            throw new IllegalArgumentException("mode must be ROOM_COMPOSITE or DUAL_PARTICIPANT");
        }
        return mode;
    }
}
