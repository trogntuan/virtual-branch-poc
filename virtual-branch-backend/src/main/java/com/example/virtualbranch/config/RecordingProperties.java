package com.example.virtualbranch.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "virtual-branch.recording")
public record RecordingProperties(
        /**
         * ROOM_COMPOSITE = Chrome grid (both sides).
         * PARTICIPANT = SDK source for one participant (lighter).
         */
        String egressMode,
        /**
         * When egressMode=PARTICIPANT: prefer AGENT or CUSTOMER identity from session.
         */
        String participantPrefer,
        /** Custom encode width (e.g. 854). Null/0 → use LiveKit preset. */
        Integer width,
        /** Custom encode height (e.g. 480). */
        Integer height,
        /** Custom framerate (e.g. 15). */
        Integer framerate,
        /** Video bitrate kbps (e.g. 1000). */
        Integer videoBitrate
) {
    public boolean useParticipantEgress() {
        return "PARTICIPANT".equalsIgnoreCase(egressMode == null ? "" : egressMode.trim());
    }

    public boolean preferCustomer() {
        return "CUSTOMER".equalsIgnoreCase(participantPrefer == null ? "" : participantPrefer.trim());
    }

    public boolean useCustomEncoding() {
        return positive(width) && positive(height);
    }

    public int resolvedWidth() {
        return positive(width) ? width : 854;
    }

    public int resolvedHeight() {
        return positive(height) ? height : 480;
    }

    public int resolvedFramerate() {
        return positive(framerate) ? framerate : 15;
    }

    public int resolvedVideoBitrate() {
        return positive(videoBitrate) ? videoBitrate : 1000;
    }

    private static boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
