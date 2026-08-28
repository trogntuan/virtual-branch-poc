package com.example.virtualbranch.session;

import com.example.virtualbranch.session.dto.AcceptCallRequest;
import com.example.virtualbranch.session.dto.RequestCallRequest;
import com.example.virtualbranch.session.dto.SessionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/calls")
public class CallController {

    private final SessionService sessionService;

    public CallController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @PostMapping
    public SessionResponse requestCall(@Valid @RequestBody RequestCallRequest request) {
        return sessionService.requestCall(request);
    }

    @GetMapping("/waiting")
    public List<SessionResponse> listWaitingCalls() {
        return sessionService.listWaitingCalls();
    }

    @PostMapping("/{sessionId}/accept")
    public SessionResponse acceptCall(
            @PathVariable String sessionId,
            @Valid @RequestBody AcceptCallRequest request
    ) {
        return sessionService.acceptCall(sessionId, request);
    }

    @PostMapping("/{sessionId}/skip")
    public SessionResponse skipCall(@PathVariable String sessionId) {
        return sessionService.skipCall(sessionId);
    }
}
