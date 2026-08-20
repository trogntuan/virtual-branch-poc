package com.example.virtualbranch.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class LiveKitTimestampConverterTest {

    @Test
    void convertsNanosecondsSinceEpoch() {
        long nanos = 1_705_315_800_000_000_000L;
        OffsetDateTime result = LiveKitTimestampConverter.toOffsetDateTime(nanos);
        assertEquals(1_705_315_800L, result.toEpochSecond());
    }

    @Test
    void convertsMillisecondsSinceEpoch() {
        long millis = 1_705_315_800_000L;
        OffsetDateTime result = LiveKitTimestampConverter.toOffsetDateTime(millis);
        assertEquals(2024, result.getYear());
        assertEquals(1, result.getMonthValue());
        assertEquals(15, result.getDayOfMonth());
    }

    @Test
    void zeroOrNegativeReturnsNull() {
        assertNull(LiveKitTimestampConverter.toOffsetDateTime(0));
        assertNull(LiveKitTimestampConverter.toOffsetDateTime(-1));
    }
}
