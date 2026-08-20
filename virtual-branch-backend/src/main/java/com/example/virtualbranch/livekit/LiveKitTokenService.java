package com.example.virtualbranch.livekit;

import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.config.LiveKitProperties;
import com.example.virtualbranch.livekit.dto.TokenResponse;
import com.example.virtualbranch.session.SessionEntity;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import io.livekit.server.CanPublish;
import io.livekit.server.CanSubscribe;
import io.livekit.server.CanPublishData;
import org.springframework.stereotype.Service;

@Service
public class LiveKitTokenService {

    private final LiveKitProperties liveKitProperties;

    public LiveKitTokenService(LiveKitProperties liveKitProperties) {
        this.liveKitProperties = liveKitProperties;
    }

    public TokenResponse generateToken(SessionEntity session, String identity, String name, ParticipantRole role) {
        if (role == null) {
            throw new BusinessException(ErrorCode.INVALID_ROLE);
        }

        boolean canPublishData = role == ParticipantRole.AGENT;

        try {
            AccessToken token = new AccessToken(liveKitProperties.apiKey(), liveKitProperties.apiSecret());
            token.setIdentity(identity);
            if (name != null && !name.isBlank()) {
                token.setName(name);
            }
            token.setTtl(6 * 60 * 60 * 1000L);
            token.addGrants(
                    new RoomJoin(true),
                    new RoomName(session.getRoomName()),
                    new CanPublish(true),
                    new CanSubscribe(true),
                    new CanPublishData(canPublishData)
            );

            return new TokenResponse(
                    liveKitProperties.wsUrl(),
                    session.getRoomName(),
                    token.toJwt()
            );
        } catch (RuntimeException exception) {
            throw new BusinessException(
                    ErrorCode.TOKEN_GENERATION_FAILED,
                    ErrorCode.TOKEN_GENERATION_FAILED.getDefaultMessage(),
                    org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }
}
