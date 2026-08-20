package com.example.virtualbranch.recording;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import livekit.LivekitEgress;
import org.junit.jupiter.api.Test;

class RecordingStatusMapperTest {

    @Test
    void mapEgressStarting() {
        assertEquals(RecordingStatus.STARTING, RecordingStatusMapper.fromEgressStatus(LivekitEgress.EgressStatus.EGRESS_STARTING));
    }

    @Test
    void mapEgressActive() {
        assertEquals(RecordingStatus.RECORDING, RecordingStatusMapper.fromEgressStatus(LivekitEgress.EgressStatus.EGRESS_ACTIVE));
    }

    @Test
    void mapEgressEnding() {
        assertEquals(RecordingStatus.STOPPING, RecordingStatusMapper.fromEgressStatus(LivekitEgress.EgressStatus.EGRESS_ENDING));
    }

    @Test
    void mapEgressComplete() {
        assertEquals(RecordingStatus.COMPLETED, RecordingStatusMapper.fromEgressStatus(LivekitEgress.EgressStatus.EGRESS_COMPLETE));
    }

    @Test
    void mapEgressFailed() {
        assertEquals(RecordingStatus.FAILED, RecordingStatusMapper.fromEgressStatus(LivekitEgress.EgressStatus.EGRESS_FAILED));
    }

    @Test
    void mapEgressAborted() {
        assertEquals(RecordingStatus.FAILED, RecordingStatusMapper.fromEgressStatus(LivekitEgress.EgressStatus.EGRESS_ABORTED));
    }

    @Test
    void terminalStatuses() {
        assertTrue(RecordingStatusMapper.isTerminalEgressStatus(LivekitEgress.EgressStatus.EGRESS_COMPLETE));
        assertTrue(RecordingStatusMapper.isTerminalEgressStatus(LivekitEgress.EgressStatus.EGRESS_FAILED));
        assertFalse(RecordingStatusMapper.isTerminalEgressStatus(LivekitEgress.EgressStatus.EGRESS_ACTIVE));
    }
}

