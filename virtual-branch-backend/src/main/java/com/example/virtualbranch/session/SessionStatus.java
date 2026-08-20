package com.example.virtualbranch.session;

public enum SessionStatus {
    /** @deprecated legacy agent-initiated sessions; new calls use WAITING */
    CREATED,
    /** Customer requested a call; waiting for agent to accept */
    WAITING,
    /** Agent accepted; call in progress */
    ACTIVE,
    ENDED
}
