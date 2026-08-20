package com.example.virtualbranch.livekit;

import com.example.virtualbranch.config.LiveKitProperties;
import com.example.virtualbranch.session.SessionEntity;
import com.example.virtualbranch.session.SessionStatus;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiveKitTokenServiceTest {

    private static final String API_KEY = "devkey";
    private static final String API_SECRET = "virtual_branch_poc_dev_secret_2026";

    private LiveKitTokenService liveKitTokenService;
    private SessionEntity session;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        liveKitTokenService = new LiveKitTokenService(new LiveKitProperties(
                "http://localhost:7880",
                "ws://localhost:7880",
                API_KEY,
                API_SECRET
        ));
        session = new SessionEntity(
                "SES-test",
                "VB-test",
                SessionStatus.CREATED,
                OffsetDateTime.now()
        );
        objectMapper = new ObjectMapper();
    }

    @Test
    void agentTokenAllowsPublishData() throws Exception {
        var response = liveKitTokenService.generateToken(session, "agent-1", "Agent Demo", ParticipantRole.AGENT);
        assertTrue(readCanPublishData(response.participantToken()));
    }

    @Test
    void customerTokenBlocksPublishData() throws Exception {
        var response = liveKitTokenService.generateToken(session, "customer-1", "Customer Demo", ParticipantRole.CUSTOMER);
        assertFalse(readCanPublishData(response.participantToken()));
    }

    private boolean readCanPublishData(String jwt) throws Exception {
        String payload = jwt.split("\\.")[1];
        byte[] decoded = Base64.getUrlDecoder().decode(payload);
        JsonNode root = objectMapper.readTree(new String(decoded, StandardCharsets.UTF_8));
        JsonNode video = root.path("video");
        if (video.isMissingNode()) {
            video = root.path("metadata").path("video");
        }
        if (video.has("canPublishData")) {
            return video.get("canPublishData").asBoolean();
        }
        return root.path("canPublishData").asBoolean(false);
    }
}
