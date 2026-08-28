package com.example.virtualbranch.session;

import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.livekit.LiveKitTokenService;
import com.example.virtualbranch.livekit.ParticipantRole;
import com.example.virtualbranch.livekit.dto.TokenRequest;
import com.example.virtualbranch.livekit.dto.TokenResponse;
import com.example.virtualbranch.session.dto.AcceptCallRequest;
import com.example.virtualbranch.session.dto.MobileDisplayRequest;
import com.example.virtualbranch.session.dto.MobileDisplayResponse;
import com.example.virtualbranch.session.dto.RequestCallRequest;
import com.example.virtualbranch.session.dto.SessionResponse;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SessionService {

    private static final Logger log = LoggerFactory.getLogger(SessionService.class);

    private final SessionRepository sessionRepository;
    private final LiveKitTokenService liveKitTokenService;

    public SessionService(SessionRepository sessionRepository, LiveKitTokenService liveKitTokenService) {
        this.sessionRepository = sessionRepository;
        this.liveKitTokenService = liveKitTokenService;
    }

    /** @deprecated Agent-initiated sessions; use {@link #requestCall(RequestCallRequest)} */
    @Transactional
    public SessionResponse createSession() {
        String uuid = UUID.randomUUID().toString();
        SessionEntity session = new SessionEntity(
                "SES-" + uuid,
                "VB-" + uuid,
                SessionStatus.CREATED,
                OffsetDateTime.now()
        );
        sessionRepository.save(session);
        log.info("Session created sessionId={} roomName={}", session.getId(), session.getRoomName());
        return toResponse(session);
    }

    @Transactional
    public SessionResponse requestCall(RequestCallRequest request) {
        MobileOrientation orientation = request.parsedOrientation();
        if (orientation == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid orientation. Use PORTRAIT or LANDSCAPE");
        }

        OffsetDateTime now = OffsetDateTime.now();
        String uuid = UUID.randomUUID().toString();
        SessionEntity session = new SessionEntity(
                "SES-" + uuid,
                "VB-" + uuid,
                SessionStatus.WAITING,
                now
        );
        session.markWaiting(request.identity(), request.name(), now);
        session.updateMobileDisplay(
                request.viewportWidth(),
                request.viewportHeight(),
                request.devicePixelRatio(),
                orientation,
                now
        );
        sessionRepository.save(session);
        log.info("Call requested sessionId={} customer={}", session.getId(), request.identity());
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public List<SessionResponse> listWaitingCalls() {
        return sessionRepository.findByStatusOrderByCreatedAtAsc(SessionStatus.WAITING).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public SessionResponse acceptCall(String sessionId, AcceptCallRequest request) {
        SessionEntity session = requireSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }
        if (session.getStatus() != SessionStatus.WAITING) {
            throw new BusinessException(ErrorCode.CALL_NOT_WAITING);
        }

        session.accept(request.identity(), request.name(), OffsetDateTime.now());
        log.info("Call accepted sessionId={} agent={}", sessionId, request.identity());
        return toResponse(session);
    }

    @Transactional
    public SessionResponse skipCall(String sessionId) {
        SessionEntity session = requireSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }
        if (session.getStatus() != SessionStatus.WAITING) {
            throw new BusinessException(ErrorCode.CALL_NOT_WAITING);
        }

        session.end(OffsetDateTime.now());
        log.info("Call skipped by agent sessionId={}", sessionId);
        return toResponse(session);
    }

    @Transactional(readOnly = true)
    public SessionResponse getSession(String sessionId) {
        return toResponse(requireOpenSession(sessionId));
    }

    @Transactional
    public SessionResponse endSession(String sessionId) {
        SessionEntity session = requireSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED, ErrorCode.SESSION_ENDED.getDefaultMessage(), HttpStatus.CONFLICT);
        }
        session.end(OffsetDateTime.now());
        log.info("Session ended sessionId={}", session.getId());
        return toResponse(session);
    }

    @Transactional
    public TokenResponse issueToken(String sessionId, TokenRequest request) {
        SessionEntity session = requireSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }

        ParticipantRole role = request.role();
        if (role == null) {
            throw new BusinessException(ErrorCode.INVALID_ROLE);
        }

        if (session.getStatus() == SessionStatus.WAITING) {
            throw new BusinessException(ErrorCode.CALL_NOT_ACCEPTED);
        }

        if (session.getStatus() == SessionStatus.CREATED) {
            session.activate();
        }

        log.info("Token issued sessionId={} role={} identity={}", sessionId, role, request.identity());
        return liveKitTokenService.generateToken(
                session,
                request.identity(),
                request.name(),
                role
        );
    }

    @Transactional
    public MobileDisplayResponse updateMobileDisplay(String sessionId, MobileDisplayRequest request) {
        SessionEntity session = requireOpenSession(sessionId);
        MobileOrientation orientation = request.parsedOrientation();
        if (orientation == null) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, "Invalid orientation. Use PORTRAIT or LANDSCAPE");
        }

        session.updateMobileDisplay(
                request.viewportWidth(),
                request.viewportHeight(),
                request.devicePixelRatio(),
                orientation,
                OffsetDateTime.now()
        );

        return toMobileDisplayResponse(session);
    }

    @Transactional(readOnly = true)
    public MobileDisplayResponse getMobileDisplay(String sessionId) {
        SessionEntity session = requireSession(sessionId);
        return toMobileDisplayResponse(session);
    }

    @Transactional(readOnly = true)
    public SessionEntity requireActiveSession(String sessionId) {
        SessionEntity session = requireSession(sessionId);
        if (session.getStatus() != SessionStatus.ACTIVE && session.getStatus() != SessionStatus.CREATED) {
            if (session.getStatus() == SessionStatus.ENDED) {
                throw new BusinessException(ErrorCode.SESSION_ENDED);
            }
            throw new BusinessException(ErrorCode.CALL_NOT_ACCEPTED);
        }
        return session;
    }

    private MobileDisplayResponse toMobileDisplayResponse(SessionEntity session) {
        return new MobileDisplayResponse(
                session.getMobileViewportWidth(),
                session.getMobileViewportHeight(),
                session.getMobileDevicePixelRatio(),
                session.getMobileOrientation() != null ? session.getMobileOrientation().name() : null
        );
    }

    private SessionEntity requireOpenSession(String sessionId) {
        SessionEntity session = requireSession(sessionId);
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED, ErrorCode.SESSION_ENDED.getDefaultMessage(), HttpStatus.CONFLICT);
        }
        return session;
    }

    public SessionEntity requireSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        ErrorCode.SESSION_NOT_FOUND.getDefaultMessage(),
                        HttpStatus.NOT_FOUND
                ));
    }

    private SessionResponse toResponse(SessionEntity session) {
        return new SessionResponse(
                session.getId(),
                session.getRoomName(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getEndedAt(),
                session.getCustomerIdentity(),
                session.getCustomerName(),
                session.getAgentIdentity(),
                session.getAgentName(),
                session.getAcceptedAt()
        );
    }
}
