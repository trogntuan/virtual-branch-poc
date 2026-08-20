package com.example.virtualbranch.session;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vb_session")
public class SessionEntity {

    @Id
    private String id;

    @Column(name = "room_name", nullable = false, unique = true)
    private String roomName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SessionStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "ended_at")
    private OffsetDateTime endedAt;

    @Column(name = "customer_identity")
    private String customerIdentity;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "agent_identity")
    private String agentIdentity;

    @Column(name = "agent_name")
    private String agentName;

    @Column(name = "accepted_at")
    private OffsetDateTime acceptedAt;

    @Column(name = "mobile_viewport_width")
    private Integer mobileViewportWidth;

    @Column(name = "mobile_viewport_height")
    private Integer mobileViewportHeight;

    @Column(name = "mobile_device_pixel_ratio")
    private Double mobileDevicePixelRatio;

    @Enumerated(EnumType.STRING)
    @Column(name = "mobile_orientation")
    private MobileOrientation mobileOrientation;

    @Column(name = "mobile_display_updated_at")
    private OffsetDateTime mobileDisplayUpdatedAt;

    protected SessionEntity() {
    }

    public SessionEntity(String id, String roomName, SessionStatus status, OffsetDateTime createdAt) {
        this.id = id;
        this.roomName = roomName;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getRoomName() {
        return roomName;
    }

    public SessionStatus getStatus() {
        return status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getEndedAt() {
        return endedAt;
    }

    public String getCustomerIdentity() {
        return customerIdentity;
    }

    public String getCustomerName() {
        return customerName;
    }

    public String getAgentIdentity() {
        return agentIdentity;
    }

    public String getAgentName() {
        return agentName;
    }

    public OffsetDateTime getAcceptedAt() {
        return acceptedAt;
    }

    public Integer getMobileViewportWidth() {
        return mobileViewportWidth;
    }

    public Integer getMobileViewportHeight() {
        return mobileViewportHeight;
    }

    public Double getMobileDevicePixelRatio() {
        return mobileDevicePixelRatio;
    }

    public MobileOrientation getMobileOrientation() {
        return mobileOrientation;
    }

    public OffsetDateTime getMobileDisplayUpdatedAt() {
        return mobileDisplayUpdatedAt;
    }

    public void activate() {
        this.status = SessionStatus.ACTIVE;
    }

    public void end(OffsetDateTime endedAt) {
        this.status = SessionStatus.ENDED;
        this.endedAt = endedAt;
    }

    public void markWaiting(String customerIdentity, String customerName, OffsetDateTime requestedAt) {
        this.status = SessionStatus.WAITING;
        this.customerIdentity = customerIdentity;
        this.customerName = customerName;
        this.createdAt = requestedAt;
    }

    public void accept(String agentIdentity, String agentName, OffsetDateTime acceptedAt) {
        this.status = SessionStatus.ACTIVE;
        this.agentIdentity = agentIdentity;
        this.agentName = agentName;
        this.acceptedAt = acceptedAt;
    }

    public void updateMobileDisplay(
            int viewportWidth,
            int viewportHeight,
            double devicePixelRatio,
            MobileOrientation orientation,
            OffsetDateTime updatedAt
    ) {
        this.mobileViewportWidth = viewportWidth;
        this.mobileViewportHeight = viewportHeight;
        this.mobileDevicePixelRatio = devicePixelRatio;
        this.mobileOrientation = orientation;
        this.mobileDisplayUpdatedAt = updatedAt;
    }
}
