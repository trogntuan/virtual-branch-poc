package com.example.virtualbranch.recording;

import livekit.LivekitEgress;

public class RecordingStatusMapper {

    public static RecordingStatus fromEgressStatus(LivekitEgress.EgressStatus status) {
        if (status == null) {
            return RecordingStatus.FAILED;
        }

        return switch (status) {
            case EGRESS_STARTING -> RecordingStatus.STARTING;
            case EGRESS_ACTIVE -> RecordingStatus.RECORDING;
            case EGRESS_ENDING -> RecordingStatus.STOPPING;
            case EGRESS_COMPLETE -> RecordingStatus.COMPLETED;
            case EGRESS_FAILED, EGRESS_ABORTED, EGRESS_LIMIT_REACHED -> RecordingStatus.FAILED;
            case UNRECOGNIZED -> RecordingStatus.FAILED;
        };
    }

    public static boolean isTerminalEgressStatus(LivekitEgress.EgressStatus status) {
        if (status == null) {
            return false;
        }
        return switch (status) {
            case EGRESS_COMPLETE, EGRESS_FAILED, EGRESS_ABORTED, EGRESS_LIMIT_REACHED -> true;
            default -> false;
        };
    }
}

