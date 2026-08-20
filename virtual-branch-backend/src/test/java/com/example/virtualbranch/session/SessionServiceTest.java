package com.example.virtualbranch.session;

import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.livekit.LiveKitTokenService;
import com.example.virtualbranch.livekit.ParticipantRole;
import com.example.virtualbranch.session.dto.AcceptCallRequest;
import com.example.virtualbranch.session.dto.RequestCallRequest;
import com.example.virtualbranch.livekit.dto.TokenRequest;
import com.example.virtualbranch.livekit.dto.TokenResponse;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private LiveKitTokenService liveKitTokenService;

    @InjectMocks
    private SessionService sessionService;

    @Test
    void createSessionPersistsCreatedStatus() {
        when(sessionRepository.save(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = sessionService.createSession();

        assertEquals(SessionStatus.CREATED, response.status());
        verify(sessionRepository).save(any(SessionEntity.class));
    }

    @Test
    void endEndedSessionThrowsConflict() {
        SessionEntity session = new SessionEntity(
                "SES-ended",
                "VB-ended",
                SessionStatus.ENDED,
                OffsetDateTime.now()
        );
        session.end(OffsetDateTime.now());
        when(sessionRepository.findById("SES-ended")).thenReturn(Optional.of(session));

        BusinessException exception = assertThrows(BusinessException.class, () -> sessionService.endSession("SES-ended"));

        assertEquals(ErrorCode.SESSION_ENDED, exception.getErrorCode());
    }

    @Test
    void issueTokenActivatesCreatedSession() {
        SessionEntity session = new SessionEntity(
                "SES-active",
                "VB-active",
                SessionStatus.CREATED,
                OffsetDateTime.now()
        );
        when(sessionRepository.findById("SES-active")).thenReturn(Optional.of(session));
        when(liveKitTokenService.generateToken(eq(session), eq("agent-1"), eq("Agent"), eq(ParticipantRole.AGENT)))
                .thenReturn(new TokenResponse("ws://localhost:7880", "VB-active", "token"));

        var response = sessionService.issueToken("SES-active", new TokenRequest("agent-1", "Agent", ParticipantRole.AGENT));

        assertEquals("token", response.participantToken());
        assertEquals(SessionStatus.ACTIVE, session.getStatus());
    }

    @Test
    void requestCallCreatesWaitingSession() {
        when(sessionRepository.save(any(SessionEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = sessionService.requestCall(
                new RequestCallRequest("cust-1", "Alice", 390, 844, 3.0, "PORTRAIT")
        );

        assertEquals(SessionStatus.WAITING, response.status());
        assertEquals("cust-1", response.customerIdentity());
        assertEquals("Alice", response.customerName());
    }

    @Test
    void issueTokenOnWaitingSessionThrows() {
        SessionEntity session = new SessionEntity(
                "SES-wait",
                "VB-wait",
                SessionStatus.WAITING,
                OffsetDateTime.now()
        );
        session.markWaiting("cust-1", "Alice", OffsetDateTime.now());
        when(sessionRepository.findById("SES-wait")).thenReturn(Optional.of(session));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> sessionService.issueToken("SES-wait", new TokenRequest("cust-1", "Alice", ParticipantRole.CUSTOMER))
        );

        assertEquals(ErrorCode.CALL_NOT_ACCEPTED, exception.getErrorCode());
    }

    @Test
    void acceptCallActivatesWaitingSession() {
        SessionEntity session = new SessionEntity(
                "SES-wait",
                "VB-wait",
                SessionStatus.WAITING,
                OffsetDateTime.now()
        );
        session.markWaiting("cust-1", "Alice", OffsetDateTime.now());
        when(sessionRepository.findById("SES-wait")).thenReturn(Optional.of(session));

        var response = sessionService.acceptCall("SES-wait", new AcceptCallRequest("agent-1", "Bob"));

        assertEquals(SessionStatus.ACTIVE, response.status());
        assertEquals("agent-1", response.agentIdentity());
    }
}
