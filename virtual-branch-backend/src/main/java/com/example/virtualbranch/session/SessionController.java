package com.example.virtualbranch.session;

import com.example.virtualbranch.livekit.dto.TokenRequest;
import com.example.virtualbranch.livekit.dto.TokenResponse;
import com.example.virtualbranch.session.dto.MobileDisplayRequest;
import com.example.virtualbranch.session.dto.MobileDisplayResponse;
import com.example.virtualbranch.session.dto.SessionResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public SessionResponse createSession() {
        return sessionService.createSession();
    }

    @GetMapping("/{sessionId}")
    public SessionResponse getSession(@PathVariable String sessionId) {
        return sessionService.getSession(sessionId);
    }

    @PostMapping("/{sessionId}/end")
    public SessionResponse endSession(@PathVariable String sessionId) {
        return sessionService.endSession(sessionId);
    }

    @PostMapping("/{sessionId}/token")
    public TokenResponse issueToken(
            @PathVariable String sessionId,
            @Valid @RequestBody TokenRequest request
    ) {
        return sessionService.issueToken(sessionId, request);
    }

    @PutMapping("/{sessionId}/mobile-display")
    public MobileDisplayResponse updateMobileDisplay(
            @PathVariable String sessionId,
            @Valid @RequestBody MobileDisplayRequest request
    ) {
        return sessionService.updateMobileDisplay(sessionId, request);
    }

    @GetMapping("/{sessionId}/mobile-display")
    public MobileDisplayResponse getMobileDisplay(@PathVariable String sessionId) {
        return sessionService.getMobileDisplay(sessionId);
    }
}
