package com.example.virtualbranch.recording;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vb_recording")
public class RecordingEntity {

    @Id
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "egress_id")
    private String egressId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecordingStatus status;

    @Column(name = "object_key")
    private String objectKey;

    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "error_message")
    private String errorMessage;

    protected RecordingEntity() {
    }

    public RecordingEntity(
            String id,
            String sessionId,
            String egressId,
            RecordingStatus status,
            String objectKey,
            OffsetDateTime startedAt,
            OffsetDateTime endedAt,
            String errorMessage
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.egressId = egressId;
        this.status = status;
        this.objectKey = objectKey;
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.errorMessage = errorMessage;
    }

    public String getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getEgressId() {
        return egressId;
    }

    public RecordingStatus getStatus() {
        return status;
    }

    public String getObjectKey() {
        return objectKey;
    }

    public OffsetDateTime getStartedAt() {
        return startedAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setEgressId(String egressId) {
        this.egressId = egressId;
    }

    public void setStatus(RecordingStatus status) {
        this.status = status;
    }

    public void setStartedAt(OffsetDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public void setEndedAt(OffsetDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

