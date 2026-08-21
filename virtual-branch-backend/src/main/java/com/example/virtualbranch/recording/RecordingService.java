package com.example.virtualbranch.recording;

import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.config.LiveKitProperties;
import com.example.virtualbranch.settings.AppSettingService;
import com.example.virtualbranch.storage.EgressPlaybackUrlService;
import com.example.virtualbranch.storage.EgressStorageConfigurer;
import com.example.virtualbranch.storage.ObjectStorageService;
import com.example.virtualbranch.storage.StorageOperationException;
import com.example.virtualbranch.recording.dto.RecordingResponse;
import com.example.virtualbranch.session.SessionEntity;
import com.example.virtualbranch.session.SessionRepository;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import livekit.LivekitEgress;
import io.livekit.server.EgressServiceClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import retrofit2.Response;
import retrofit2.Call;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordingService {

    private static final Logger log = LoggerFactory.getLogger(RecordingService.class);
    private static final String OBJECT_PREFIX = "recordings/";
    private static final String OBJECT_SUFFIX = ".mp4";
    private static final int PLAYBACK_URL_EXPIRY_SECONDS = 600;
    private static final String EGRESS_ERROR_TRUNCATE_HINT = " (truncated)";

    private final SessionRepository sessionRepository;
    private final RecordingRepository recordingRepository;
    private final LiveKitProperties liveKitProperties;
    private final AppSettingService appSettingService;
    private final ObjectStorageService objectStorageService;
    private final EgressPlaybackUrlService egressPlaybackUrlService;
    private final EgressStorageConfigurer egressStorageConfigurer;

    public RecordingService(
            SessionRepository sessionRepository,
            RecordingRepository recordingRepository,
            LiveKitProperties liveKitProperties,
            AppSettingService appSettingService,
            ObjectStorageService objectStorageService,
            EgressPlaybackUrlService egressPlaybackUrlService,
            EgressStorageConfigurer egressStorageConfigurer
    ) {
        this.sessionRepository = sessionRepository;
        this.recordingRepository = recordingRepository;
        this.liveKitProperties = liveKitProperties;
        this.appSettingService = appSettingService;
        this.objectStorageService = objectStorageService;
        this.egressPlaybackUrlService = egressPlaybackUrlService;
        this.egressStorageConfigurer = egressStorageConfigurer;
    }

    @Transactional
    public RecordingResponse startRecording(String sessionId) {
        if (!appSettingService.isRecordingEnabled()) {
            log.info("Recording start rejected (disabled in DB) sessionId={}", sessionId);
            throw new BusinessException(
                    ErrorCode.RECORDING_DISABLED,
                    ErrorCode.RECORDING_DISABLED.getDefaultMessage(),
                    HttpStatus.SERVICE_UNAVAILABLE
            );
        }

        SessionEntity session = requireSession(sessionId);
        if (session.getStatus() == com.example.virtualbranch.session.SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }
        if (session.getStatus() == com.example.virtualbranch.session.SessionStatus.WAITING) {
            throw new BusinessException(ErrorCode.CALL_NOT_ACCEPTED);
        }

        String recordingId = "REC-" + UUID.randomUUID();
        String objectKey = OBJECT_PREFIX + sessionId + "/" + recordingId + OBJECT_SUFFIX;

        RecordingEntity recording = new RecordingEntity(
                recordingId,
                sessionId,
                null,
                RecordingStatus.REQUESTED,
                objectKey,
                OffsetDateTime.now(),
                null,
                null
        );
        recordingRepository.save(recording);
        log.info("Recording start requested sessionId={} recordingId={}", sessionId, recordingId);

        EgressServiceClient egressClient = createEgressClient();
        validateEgressStorageForLiveKitCloud();

        LivekitEgress.EncodedFileOutput.Builder outputBuilder = LivekitEgress.EncodedFileOutput.newBuilder()
                .setFileType(LivekitEgress.EncodedFileType.MP4)
                // objectKey is relative to the bucket root (eg: recordings/<session>/<rec>.mp4)
                .setFilepath(objectKey);
        attachStorageUpload(outputBuilder);
        LivekitEgress.EncodedFileOutput encodedOutput = outputBuilder.build();

        try {
            Call<LivekitEgress.EgressInfo> call = egressClient.startRoomCompositeEgress(
                    session.getRoomName(),
                    encodedOutput,
                    "grid",
                    LivekitEgress.EncodingOptionsPreset.H264_720P_30,
                    null,
                    false,
                    false,
                    "",
                    io.livekit.server.AudioMixing.DEFAULT_MIXING
            );
            Response<LivekitEgress.EgressInfo> response = call.execute();
            if (!response.isSuccessful() || response.body() == null) {
                throw new BusinessException(
                        ErrorCode.RECORDING_START_FAILED,
                        "LiveKit Egress start failed: HTTP " + response.code(),
                        HttpStatus.BAD_GATEWAY
                );
            }

            LivekitEgress.EgressInfo egressInfo = response.body();
            recording.setEgressId(egressInfo.getEgressId());
            recording.setStatus(RecordingStatus.STARTING);
            recordingRepository.save(recording);
            log.info("Recording started recordingId={} egressId={}", recordingId, egressInfo.getEgressId());

            return toResponse(recording, null);
        } catch (BusinessException e) {
            recording.setStatus(RecordingStatus.FAILED);
            recording.setErrorMessage(truncate(e.getMessage(), 1000));
            recordingRepository.save(recording);
            throw e;
        } catch (IOException e) {
            recording.setStatus(RecordingStatus.FAILED);
            recording.setErrorMessage(truncate(e.getMessage(), 1000));
            recordingRepository.save(recording);
            throw new BusinessException(
                    ErrorCode.RECORDING_START_FAILED,
                    ErrorCode.RECORDING_START_FAILED.getDefaultMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        } catch (RuntimeException e) {
            recording.setStatus(RecordingStatus.FAILED);
            recording.setErrorMessage(truncate(e.getMessage(), 1000));
            recordingRepository.save(recording);
            throw new BusinessException(
                    ErrorCode.RECORDING_START_FAILED,
                    ErrorCode.RECORDING_START_FAILED.getDefaultMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    @Transactional
    public RecordingResponse stopRecording(String recordingId) {
        RecordingEntity recording = requireRecording(recordingId);
        if (recording.getEgressId() == null || recording.getEgressId().isBlank()) {
            throw new BusinessException(
                    ErrorCode.RECORDING_STOP_FAILED,
                    "Missing egressId. Recording not started yet."
            );
        }

        if (recording.getStatus() == RecordingStatus.COMPLETED || recording.getStatus() == RecordingStatus.FAILED) {
            return toResponse(
                    recording,
                    recording.getStatus() == RecordingStatus.COMPLETED
                            ? presignPlaybackUrl(recording.getObjectKey())
                            : null
            );
        }

        SessionEntity session = requireSession(recording.getSessionId());
        EgressServiceClient egressClient = createEgressClient();

        try {
            Optional<LivekitEgress.EgressInfo> current = findEgressInfo(
                    egressClient,
                    session.getRoomName(),
                    recording.getEgressId()
            );
            if (current.isPresent() && RecordingStatusMapper.isTerminalEgressStatus(current.get().getStatus())) {
                applyEgressStatus(
                        recording,
                        current.get(),
                        RecordingStatusMapper.fromEgressStatus(current.get().getStatus())
                );
                recordingRepository.save(recording);
                log.info("Recording already finished recordingId={} egressStatus={}",
                        recordingId, current.get().getStatus());
                return toResponse(
                        recording,
                        recording.getStatus() == RecordingStatus.COMPLETED
                                ? presignPlaybackUrl(recording.getObjectKey())
                                : null
                );
            }

            recording.setStatus(RecordingStatus.STOPPING);
            recordingRepository.save(recording);
            log.info("Recording stop requested recordingId={} egressId={}", recordingId, recording.getEgressId());

            Response<LivekitEgress.EgressInfo> response = egressClient.stopEgress(recording.getEgressId()).execute();
            if (response.isSuccessful()) {
                return toResponse(recording, null);
            }

            // LiveKit returns 412 when egress is already complete/aborting — treat as idempotent stop.
            if (response.code() == 412) {
                Optional<LivekitEgress.EgressInfo> synced = findEgressInfo(
                        egressClient,
                        session.getRoomName(),
                        recording.getEgressId()
                );
                if (synced.isPresent()) {
                    applyEgressStatus(
                            recording,
                            synced.get(),
                            RecordingStatusMapper.fromEgressStatus(synced.get().getStatus())
                    );
                    recordingRepository.save(recording);
                    log.info("Recording stop no-op (412), synced status recordingId={}", recordingId);
                    return toResponse(
                            recording,
                            recording.getStatus() == RecordingStatus.COMPLETED
                                    ? presignPlaybackUrl(recording.getObjectKey())
                                    : null
                    );
                }
            }

            throw new BusinessException(
                    ErrorCode.RECORDING_STOP_FAILED,
                    "LiveKit Egress stop failed: HTTP " + response.code(),
                    HttpStatus.BAD_GATEWAY
            );
        } catch (BusinessException e) {
            recording.setStatus(RecordingStatus.FAILED);
            recording.setErrorMessage(truncate(e.getMessage(), 1000));
            recordingRepository.save(recording);
            throw e;
        } catch (IOException e) {
            recording.setStatus(RecordingStatus.FAILED);
            recording.setErrorMessage(truncate(e.getMessage(), 1000));
            recordingRepository.save(recording);
            throw new BusinessException(ErrorCode.RECORDING_STOP_FAILED);
        } catch (RuntimeException e) {
            recording.setStatus(RecordingStatus.FAILED);
            recording.setErrorMessage(truncate(e.getMessage(), 1000));
            recordingRepository.save(recording);
            throw new BusinessException(ErrorCode.RECORDING_STOP_FAILED);
        }
    }

    @Transactional
    public RecordingResponse getRecording(String recordingId) {
        RecordingEntity recording = requireRecording(recordingId);
        SessionEntity session = requireSession(recording.getSessionId());

        if (recording.getEgressId() == null || recording.getEgressId().isBlank()) {
            return toResponse(recording, null);
        }

        if (recording.getStatus() == RecordingStatus.COMPLETED || recording.getStatus() == RecordingStatus.FAILED) {
            return toResponse(recording, recording.getStatus() == RecordingStatus.COMPLETED ? presignPlaybackUrl(recording.getObjectKey()) : null);
        }

        EgressServiceClient egressClient = createEgressClient();
        try {
            Optional<LivekitEgress.EgressInfo> egressInfo = findEgressInfo(
                    egressClient,
                    session.getRoomName(),
                    recording.getEgressId()
            );
            if (egressInfo.isPresent()) {
                LivekitEgress.EgressInfo info = egressInfo.get();
                RecordingStatus newStatus = RecordingStatusMapper.fromEgressStatus(info.getStatus());
                applyEgressStatus(recording, info, newStatus);
                recordingRepository.save(recording);
                if (newStatus == RecordingStatus.COMPLETED) {
                    log.info("Recording completed recordingId={} objectKey={}", recordingId, recording.getObjectKey());
                } else if (newStatus == RecordingStatus.FAILED) {
                    log.warn("Recording failed recordingId={}", recordingId);
                }

                return toResponse(
                        recording,
                        recording.getStatus() == RecordingStatus.COMPLETED
                                ? presignPlaybackUrl(recording.getObjectKey())
                                : null
                );
            }
        } catch (IOException e) {
            // Keep last known status; polling will retry later.
            return toResponse(recording, null);
        }

        return toResponse(recording, null);
    }

    private Optional<LivekitEgress.EgressInfo> findEgressInfo(
            EgressServiceClient egressClient,
            String roomName,
            String egressId
    ) throws IOException {
        Response<List<LivekitEgress.EgressInfo>> response = egressClient.listEgress(roomName).execute();
        if (!response.isSuccessful() || response.body() == null) {
            return Optional.empty();
        }

        return response.body().stream()
                .filter(info -> info != null && egressId.equals(info.getEgressId()))
                .findFirst();
    }

    private void applyEgressStatus(RecordingEntity recording, LivekitEgress.EgressInfo egressInfo, RecordingStatus newStatus) {
        recording.setStatus(newStatus);

        if (newStatus == RecordingStatus.COMPLETED || newStatus == RecordingStatus.FAILED) {
            OffsetDateTime endedAt = LiveKitTimestampConverter.toOffsetDateTime(egressInfo.getEndedAt());
            if (endedAt != null) {
                recording.setEndedAt(endedAt);
            }
        }

        if (egressInfo.getStartedAt() > 0 && recording.getStartedAt() == null) {
            OffsetDateTime startedAt = LiveKitTimestampConverter.toOffsetDateTime(egressInfo.getStartedAt());
            if (startedAt != null) {
                recording.setStartedAt(startedAt);
            }
        }

        if (egressInfo.getError() != null && !egressInfo.getError().isBlank()) {
            recording.setErrorMessage(truncate(egressInfo.getError(), 1000));
        }
    }

    private RecordingResponse toResponse(RecordingEntity recording, String playbackUrl) {
        return new RecordingResponse(
                recording.getId(),
                recording.getSessionId(),
                recording.getEgressId(),
                recording.getStatus(),
                recording.getObjectKey(),
                playbackUrl,
                recording.getErrorMessage()
        );
    }

    private SessionEntity requireSession(String sessionId) {
        return sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        ErrorCode.SESSION_NOT_FOUND.getDefaultMessage(),
                        HttpStatus.NOT_FOUND
                ));
    }

    private RecordingEntity requireRecording(String recordingId) {
        return recordingRepository.findById(recordingId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.RECORDING_NOT_FOUND,
                        ErrorCode.RECORDING_NOT_FOUND.getDefaultMessage(),
                        HttpStatus.NOT_FOUND
                ));
    }

    private EgressServiceClient createEgressClient() {
        // LiveKit Cloud and self-hosted LiveKit expose Egress APIs on the same API URL.
        return EgressServiceClient.create(
                liveKitProperties.apiUrl(),
                liveKitProperties.apiKey(),
                liveKitProperties.apiSecret(),
                false
        );
    }

    private void validateEgressStorageForLiveKitCloud() {
        if (!isLiveKitCloud()) {
            return;
        }
        if (egressStorageConfigurer.hasEgressCredentials()) {
            return;
        }
        throw new BusinessException(
                ErrorCode.RECORDING_START_FAILED,
                "LiveKit Cloud recording requires egress storage. "
                        + "Set VB_EGRESS_* for Cloudflare R2 (recommended), "
                        + "or GOOGLE_APPLICATION_CREDENTIALS for GCS GCPUpload.",
                HttpStatus.BAD_REQUEST
        );
    }

    private boolean isLiveKitCloud() {
        return containsLiveKitCloudHost(liveKitProperties.apiUrl())
                || containsLiveKitCloudHost(liveKitProperties.wsUrl());
    }

    private static boolean containsLiveKitCloudHost(String url) {
        return url != null && url.toLowerCase().contains("livekit.cloud");
    }

    private void attachStorageUpload(LivekitEgress.EncodedFileOutput.Builder outputBuilder) {
        if (isLiveKitCloud() && egressStorageConfigurer.isLocalEndpoint()) {
            log.warn("Skipping local storage upload for LiveKit Cloud egress");
            return;
        }
        egressStorageConfigurer.attach(outputBuilder);
    }

    private String presignPlaybackUrl(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) {
            return null;
        }
        try {
            if (egressPlaybackUrlService.isConfigured()) {
                return egressPlaybackUrlService.presignGetUrl(objectKey, PLAYBACK_URL_EXPIRY_SECONDS);
            }
            return objectStorageService.presignGetUrl(objectKey, PLAYBACK_URL_EXPIRY_SECONDS);
        } catch (StorageOperationException e) {
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED, "Failed to generate playback URL");
        }
    }

    private static String truncate(String value, int maxLen) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLen) {
            return value;
        }
        return value.substring(0, maxLen - EGRESS_ERROR_TRUNCATE_HINT.length()) + EGRESS_ERROR_TRUNCATE_HINT;
    }
}

