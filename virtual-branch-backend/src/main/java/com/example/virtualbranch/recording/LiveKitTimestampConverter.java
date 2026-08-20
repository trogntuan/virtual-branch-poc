package com.example.virtualbranch.recording;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

final class LiveKitTimestampConverter {

    private static final long NANOS_THRESHOLD = 1_000_000_000_000_000L;
    private static final long MILLIS_THRESHOLD = 1_000_000_000_000L;

    private LiveKitTimestampConverter() {
    }

    static OffsetDateTime toOffsetDateTime(long timestamp) {
        if (timestamp <= 0) {
            return null;
        }

        Instant instant;
        if (timestamp >= NANOS_THRESHOLD) {
            instant = Instant.ofEpochSecond(
                    timestamp / 1_000_000_000L,
                    timestamp % 1_000_000_000L
            );
        } else if (timestamp >= MILLIS_THRESHOLD) {
            instant = Instant.ofEpochMilli(timestamp);
        } else {
            instant = Instant.ofEpochSecond(timestamp);
        }

        return instant.atOffset(ZoneOffset.UTC);
    }
}
