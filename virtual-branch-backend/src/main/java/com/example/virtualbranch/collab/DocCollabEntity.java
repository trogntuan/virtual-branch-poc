package com.example.virtualbranch.collab;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vb_doc_collab")
public class DocCollabEntity {

    @Id
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "document_id", nullable = false)
    private String documentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocCollabStatus status;

    @Column(name = "current_page")
    private Integer currentPage;

    @Column(name = "requested_at")
    private OffsetDateTime requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "consent_decision")
    private ConsentDecision consentDecision;

    @Column(name = "consent_at")
    private OffsetDateTime consentAt;

    @Column(name = "current_scroll_ratio")
    private Double currentScrollRatio;

    @Column(name = "view_mode")
    private String viewMode;

    @Column(name = "zoom_scale")
    private Double zoomScale;

    @Column(name = "started_at", nullable = false)
    private OffsetDateTime startedAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "end_reason")
    private String endReason;

    protected DocCollabEntity() {
    }

    public DocCollabEntity(
            String id,
            String sessionId,
            String documentId,
            DocCollabStatus status,
            OffsetDateTime requestedAt,
            OffsetDateTime startedAt
    ) {
        this.id = id;
        this.sessionId = sessionId;
        this.documentId = documentId;
        this.status = status;
        this.requestedAt = requestedAt;
        this.startedAt = startedAt;
        this.viewMode = "FIT_WIDTH";
        this.zoomScale = 1.0;
        this.currentScrollRatio = 0.0;
        this.currentPage = 1;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getDocumentId() { return documentId; }
    public DocCollabStatus getStatus() { return status; }
    public Integer getCurrentPage() { return currentPage; }
    public OffsetDateTime getRequestedAt() { return requestedAt; }
    public ConsentDecision getConsentDecision() { return consentDecision; }
    public OffsetDateTime getConsentAt() { return consentAt; }
    public Double getCurrentScrollRatio() { return currentScrollRatio; }
    public String getViewMode() { return viewMode; }
    public Double getZoomScale() { return zoomScale; }
    public OffsetDateTime getStartedAt() { return startedAt; }
    public OffsetDateTime getEndedAt() { return endedAt; }
    public String getEndReason() { return endReason; }

    public void setStatus(DocCollabStatus status) { this.status = status; }
    public void setConsentDecision(ConsentDecision consentDecision) { this.consentDecision = consentDecision; }
    public void setConsentAt(OffsetDateTime consentAt) { this.consentAt = consentAt; }
    public void setEndedAt(OffsetDateTime endedAt) { this.endedAt = endedAt; }
    public void setEndReason(String endReason) { this.endReason = endReason; }
    public void setCurrentPage(Integer currentPage) { this.currentPage = currentPage; }
    public void setCurrentScrollRatio(Double currentScrollRatio) { this.currentScrollRatio = currentScrollRatio; }
    public void setViewMode(String viewMode) { this.viewMode = viewMode; }
    public void setZoomScale(Double zoomScale) { this.zoomScale = zoomScale; }
}
