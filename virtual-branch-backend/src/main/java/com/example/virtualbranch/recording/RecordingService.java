package com.example.virtualbranch.recording;

import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.config.LiveKitProperties;
import com.example.virtualbranch.config.RecordingProperties;
import com.example.virtualbranch.recording.dto.RecordingResponse;
import com.example.virtualbranch.recording.dto.RecordingTrackResponse;
import com.example.virtualbranch.session.SessionEntity;
import com.example.virtualbranch.session.SessionRepository;
import com.example.virtualbranch.settings.AppSettingService;
import com.example.virtualbranch.settings.RecordingMode;
import com.example.virtualbranch.storage.EgressPlaybackUrlService;
import com.example.virtualbranch.storage.EgressStorageConfigurer;
import com.example.virtualbranch.storage.ObjectStorageService;
import com.example.virtualbranch.storage.StorageOperationException;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import livekit.LivekitEgress;
import livekit.LivekitModels;
import io.livekit.server.EgressServiceClient;
import io.livekit.server.EncodedOutputs;
import io.livekit.server.RoomServiceClient;
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
    private static final String SIDE_COMPOSITE = "COMPOSITE";
    private static final String SIDE_AGENT = "AGENT";
    private static final String SIDE_CUSTOMER = "CUSTOMER";

    private final SessionRepository sessionRepository;
    private final RecordingRepository recordingRepository;
    private final LiveKitProperties liveKitProperties;
    private final RecordingProperties recordingProperties;
    private final AppSettingService appSettingService;
    private final ObjectStorageService objectStorageService;
    private final EgressPlaybackUrlService egressPlaybackUrlService;
    private final EgressStorageConfigurer egressStorageConfigurer;

    public RecordingService(
            SessionRepository sessionRepository,
            RecordingRepository recordingRepository,
            LiveKitProperties liveKitProperties,
            RecordingProperties recordingProperties,
            AppSettingService appSettingService,
            ObjectStorageService objectStorageService,
            EgressPlaybackUrlService egressPlaybackUrlService,
            EgressStorageConfigurer egressStorageConfigurer
    ) {
        this.sessionRepository = sessionRepository;
        this.recordingRepository = recordingRepository;
        this.liveKitProperties = liveKitProperties;
        this.recordingProperties = recordingProperties;
        this.appSettingService = appSettingService;
        this.objectStorageService = objectStorageService;
        this.egressPlaybackUrlService = egressPlaybackUrlService;
        this.egressStorageConfigurer = egressStorageConfigurer;
    }

    public RecordingResponse startRecording(String sessionId) {
        SessionEntity session = requireSession(sessionId);
        if (session.getStatus() == com.example.virtualbranch.session.SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }
        if (session.getStatus() == com.example.virtualbranch.session.SessionStatus.WAITING) {
            throw new BusinessException(ErrorCode.CALL_NOT_ACCEPTED);
        }

        RecordingMode mode = appSettingService.getRecordingMode();
        log.info(
                "Recording start requested sessionId={} mode={} encode={}x{}@{}fps {}kbps",
                sessionId,
                mode,
                recordingProperties.resolvedWidth(),
                recordingProperties.resolvedHeight(),
                recordingProperties.resolvedFramerate(),
                recordingProperties.resolvedVideoBitrate()
        );

        EgressServiceClient egressClient = createEgressClient();
        validateEgressStorageForLiveKitCloud();

        if (mode == RecordingMode.DUAL_PARTICIPANT) {
            return startDualParticipantRecording(session, egressClient);
        }
        return startRoomCompositeRecording(session, egressClient);
    }

    private RecordingResponse startRoomCompositeRecording(
            SessionEntity session,
            EgressServiceClient egressClient
    ) {
        String sessionId = session.getId();
        String recordingId = "REC-" + UUID.randomUUID();
        String objectKey = OBJECT_PREFIX + sessionId + "/" + recordingId + OBJECT_SUFFIX;

        RecordingEntity recording = newRecording(
                recordingId,
                sessionId,
                objectKey,
                RecordingMode.ROOM_COMPOSITE.name(),
                SIDE_COMPOSITE,
                null
        );
        recordingRepository.save(recording);

        LivekitEgress.EncodedFileOutput encodedOutput = buildEncodedOutput(objectKey);

        try {
            Response<LivekitEgress.EgressInfo> response =
                    startRoomCompositeEgressCall(egressClient, session, encodedOutput).execute();
            applyStartSuccess(recording, response);
            recordingRepository.save(recording);
            log.info("Recording started recordingId={} egressId={} mode={}",
                    recordingId, recording.getEgressId(), RecordingMode.ROOM_COMPOSITE);
            return toResponse(recording);
        } catch (BusinessException e) {
            markFailed(recording, e.getMessage());
            throw e;
        } catch (IOException | RuntimeException e) {
            markFailed(recording, e.getMessage());
            throw new BusinessException(
                    ErrorCode.RECORDING_START_FAILED,
                    ErrorCode.RECORDING_START_FAILED.getDefaultMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private RecordingResponse startDualParticipantRecording(
            SessionEntity session,
            EgressServiceClient egressClient
    ) {
        String sessionId = session.getId();
        String groupId = "GRP-" + UUID.randomUUID();

        String agentRecordingId = "REC-" + UUID.randomUUID();
        String customerRecordingId = "REC-" + UUID.randomUUID();
        String agentObjectKey = OBJECT_PREFIX + sessionId + "/" + agentRecordingId + "-agent" + OBJECT_SUFFIX;
        String customerObjectKey = OBJECT_PREFIX + sessionId + "/" + customerRecordingId + "-customer" + OBJECT_SUFFIX;

        RecordingEntity agentRecording = newRecording(
                agentRecordingId,
                sessionId,
                agentObjectKey,
                RecordingMode.DUAL_PARTICIPANT.name(),
                SIDE_AGENT,
                groupId
        );
        RecordingEntity customerRecording = newRecording(
                customerRecordingId,
                sessionId,
                customerObjectKey,
                RecordingMode.DUAL_PARTICIPANT.name(),
                SIDE_CUSTOMER,
                groupId
        );
        recordingRepository.save(agentRecording);
        recordingRepository.save(customerRecording);

        try {
            // Wait until both sides are actually in the LiveKit room (auto-record often races KH join).
            DualIdentities identities = waitForDualIdentitiesInRoom(session, Duration.ofSeconds(20));
            if (identities.agentIdentity() == null && identities.customerIdentity() == null) {
                throw new BusinessException(
                        ErrorCode.RECORDING_START_FAILED,
                        "No agent/customer participant currently in the LiveKit room for DUAL_PARTICIPANT",
                        HttpStatus.BAD_REQUEST
                );
            }

            if (identities.agentIdentity() != null) {
                LivekitEgress.EncodedFileOutput agentOutput = buildEncodedOutput(agentObjectKey);
                Response<LivekitEgress.EgressInfo> agentResponse =
                        startParticipantEgressCall(egressClient, session, identities.agentIdentity(), agentOutput)
                                .execute();
                applyStartSuccess(agentRecording, agentResponse);
                recordingRepository.save(agentRecording);
            } else {
                markFailed(agentRecording, "Agent participant not in room when dual recording started");
            }

            if (identities.customerIdentity() != null) {
                try {
                    LivekitEgress.EncodedFileOutput customerOutput = buildEncodedOutput(customerObjectKey);
                    Response<LivekitEgress.EgressInfo> customerResponse =
                            startParticipantEgressCall(
                                    egressClient,
                                    session,
                                    identities.customerIdentity(),
                                    customerOutput
                            ).execute();
                    applyStartSuccess(customerRecording, customerResponse);
                    recordingRepository.save(customerRecording);
                } catch (Exception customerFailure) {
                    String detail = customerFailure instanceof BusinessException be
                            ? be.getMessage()
                            : customerFailure.getMessage();
                    markFailed(customerRecording, detail);
                    log.warn(
                            "Customer participant egress failed groupId={} detail={} — keeping agent track if started",
                            groupId,
                            detail
                    );
                }
            } else {
                markFailed(customerRecording, "Customer participant not in room when dual recording started");
            }

            if (agentRecording.getEgressId() == null && customerRecording.getEgressId() == null) {
                throw new BusinessException(
                        ErrorCode.RECORDING_START_FAILED,
                        "Failed to start any participant egress for DUAL_PARTICIPANT",
                        HttpStatus.BAD_GATEWAY
                );
            }

            log.info(
                    "Dual recording started groupId={} agentRecordingId={} customerRecordingId={} agentEgress={} customerEgress={}",
                    groupId,
                    agentRecordingId,
                    customerRecordingId,
                    agentRecording.getEgressId(),
                    customerRecording.getEgressId()
            );
            return toResponse(agentRecording);
        } catch (BusinessException e) {
            if (agentRecording.getStatus() != RecordingStatus.FAILED && agentRecording.getEgressId() == null) {
                markFailed(agentRecording, e.getMessage());
            }
            if (customerRecording.getStatus() != RecordingStatus.FAILED && customerRecording.getEgressId() == null) {
                markFailed(customerRecording, e.getMessage());
            }
            throw e;
        } catch (IOException | RuntimeException e) {
            if (agentRecording.getStatus() != RecordingStatus.FAILED && agentRecording.getEgressId() == null) {
                markFailed(agentRecording, e.getMessage());
            }
            if (customerRecording.getStatus() != RecordingStatus.FAILED && customerRecording.getEgressId() == null) {
                markFailed(customerRecording, e.getMessage());
            }
            throw new BusinessException(
                    ErrorCode.RECORDING_START_FAILED,
                    ErrorCode.RECORDING_START_FAILED.getDefaultMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private RecordingEntity newRecording(
            String recordingId,
            String sessionId,
            String objectKey,
            String mode,
            String side,
            String groupId
    ) {
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
        recording.setMode(mode);
        recording.setSide(side);
        recording.setGroupId(groupId);
        return recording;
    }

    private LivekitEgress.EncodedFileOutput buildEncodedOutput(String objectKey) {
        LivekitEgress.EncodedFileOutput.Builder outputBuilder = LivekitEgress.EncodedFileOutput.newBuilder()
                .setFileType(LivekitEgress.EncodedFileType.MP4)
                .setFilepath(objectKey);
        attachStorageUpload(outputBuilder);
        return outputBuilder.build();
    }

    private void applyStartSuccess(
            RecordingEntity recording,
            Response<LivekitEgress.EgressInfo> response
    ) throws IOException {
        if (!response.isSuccessful() || response.body() == null) {
            String detail = "HTTP " + response.code();
            try {
                if (response.errorBody() != null) {
                    detail = detail + ": " + response.errorBody().string();
                }
            } catch (Exception ignored) {
                // keep status code only
            }
            throw new BusinessException(
                    ErrorCode.RECORDING_START_FAILED,
                    "LiveKit Egress start failed: " + detail
                            + " (often egress CPU admission — lower cpu_cost in infra/egress.yaml or add egress workers)",
                    HttpStatus.BAD_GATEWAY
            );
        }
        LivekitEgress.EgressInfo egressInfo = response.body();
        recording.setEgressId(egressInfo.getEgressId());
        recording.setStatus(RecordingStatus.STARTING);
    }

    private void markFailed(RecordingEntity recording, String message) {
        recording.setStatus(RecordingStatus.FAILED);
        recording.setErrorMessage(truncate(message, 1000));
        recordingRepository.save(recording);
    }

    private void stopEgressQuietly(EgressServiceClient egressClient, String egressId) {
        if (egressId == null || egressId.isBlank()) {
            return;
        }
        try {
            egressClient.stopEgress(egressId).execute();
        } catch (Exception e) {
            log.warn("Failed to stop egress during dual-start rollback egressId={}", egressId);
        }
    }

    private Call<LivekitEgress.EgressInfo> startRoomCompositeEgressCall(
            EgressServiceClient egressClient,
            SessionEntity session,
            LivekitEgress.EncodedFileOutput encodedOutput
    ) {
        LivekitEgress.EncodingOptionsPreset preset = null;
        LivekitEgress.EncodingOptions advanced = null;
        if (recordingProperties.useCustomEncoding()) {
            advanced = buildEncodingOptions();
        } else {
            preset = LivekitEgress.EncodingOptionsPreset.H264_720P_30;
        }
        return egressClient.startRoomCompositeEgress(
                session.getRoomName(),
                encodedOutput,
                "grid",
                preset,
                advanced,
                false,
                false,
                "",
                io.livekit.server.AudioMixing.DEFAULT_MIXING
        );
    }

    private Call<LivekitEgress.EgressInfo> startParticipantEgressCall(
            EgressServiceClient egressClient,
            SessionEntity session,
            String identity,
            LivekitEgress.EncodedFileOutput encodedOutput
    ) {
        EncodedOutputs outputs = new EncodedOutputs(encodedOutput, null, null, null);
        LivekitEgress.EncodingOptionsPreset preset = null;
        LivekitEgress.EncodingOptions advanced = null;
        if (recordingProperties.useCustomEncoding()) {
            advanced = buildEncodingOptions();
        } else {
            preset = LivekitEgress.EncodingOptionsPreset.H264_720P_30;
        }
        log.info(
                "Starting PARTICIPANT egress room={} identity={} screenShare=false",
                session.getRoomName(),
                identity
        );
        return egressClient.startParticipantEgress(
                session.getRoomName(),
                identity,
                outputs,
                false,
                preset,
                advanced
        );
    }

    private LivekitEgress.EncodingOptions buildEncodingOptions() {
        return LivekitEgress.EncodingOptions.newBuilder()
                .setWidth(recordingProperties.resolvedWidth())
                .setHeight(recordingProperties.resolvedHeight())
                .setFramerate(recordingProperties.resolvedFramerate())
                .setVideoBitrate(recordingProperties.resolvedVideoBitrate())
                .setVideoCodec(livekit.LivekitModels.VideoCodec.H264_MAIN)
                .setAudioCodec(livekit.LivekitModels.AudioCodec.AAC)
                .setAudioBitrate(128)
                .setAudioFrequency(44100)
                .build();
    }

    private DualIdentities waitForDualIdentitiesInRoom(SessionEntity session, Duration timeout)
            throws IOException {
        long deadlineMs = System.currentTimeMillis() + Math.max(1_000L, timeout.toMillis());
        DualIdentities latest = resolvePresentDualIdentities(session);
        while (System.currentTimeMillis() < deadlineMs) {
            if (latest.agentIdentity() != null && latest.customerIdentity() != null) {
                return latest;
            }
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
            latest = resolvePresentDualIdentities(session);
        }
        log.info(
                "Dual identity wait finished room={} agentPresent={} customerPresent={}",
                session.getRoomName(),
                latest.agentIdentity() != null,
                latest.customerIdentity() != null
        );
        return latest;
    }

    /**
     * Only return identities that are currently present in the LiveKit room.
     * Session DB identities alone are not enough — Participant egress fails with 404 otherwise.
     */
    private DualIdentities resolvePresentDualIdentities(SessionEntity session) throws IOException {
        List<LivekitModels.ParticipantInfo> participants = listRoomParticipants(session);
        Set<String> present = participants.stream()
                .map(LivekitModels.ParticipantInfo::getIdentity)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        String sessionAgent = blankToNull(session.getAgentIdentity());
        String sessionCustomer = blankToNull(session.getCustomerIdentity());

        String agentIdentity = sessionAgent != null && present.contains(sessionAgent) ? sessionAgent : null;
        String customerIdentity =
                sessionCustomer != null && present.contains(sessionCustomer) ? sessionCustomer : null;

        if (agentIdentity == null) {
            agentIdentity = pickPresentIdentity(present, sessionAgent, sessionCustomer, true);
        }
        if (customerIdentity == null) {
            customerIdentity = pickPresentIdentity(present, sessionCustomer, agentIdentity, false);
        }

        if (agentIdentity == null || customerIdentity == null) {
            final String agentRef = agentIdentity;
            final String customerRef = customerIdentity;
            List<String> others = present.stream()
                    .filter(id -> (agentRef == null || !id.equals(agentRef))
                            && (customerRef == null || !id.equals(customerRef)))
                    .filter(id -> !id.startsWith("EG_") && !id.toLowerCase().contains("egress"))
                    .toList();
            for (String candidate : others) {
                if (agentIdentity == null) {
                    agentIdentity = candidate;
                } else if (customerIdentity == null && !candidate.equals(agentIdentity)) {
                    customerIdentity = candidate;
                }
            }
        }

        return new DualIdentities(agentIdentity, customerIdentity);
    }

    private static String pickPresentIdentity(
            Set<String> present,
            String preferred,
            String excluded,
            boolean preferAgentHint
    ) {
        if (preferred != null && present.contains(preferred)) {
            return preferred;
        }
        for (String id : present) {
            if (excluded != null && excluded.equals(id)) {
                continue;
            }
            String lower = id.toLowerCase();
            if (preferAgentHint && (lower.contains("agent") || lower.startsWith("agt"))) {
                return id;
            }
            if (!preferAgentHint && (lower.contains("cust") || lower.contains("customer") || lower.contains("client"))) {
                return id;
            }
        }
        return null;
    }

    private List<LivekitModels.ParticipantInfo> listRoomParticipants(SessionEntity session) throws IOException {
        RoomServiceClient roomClient = RoomServiceClient.create(
                liveKitProperties.apiUrl(),
                liveKitProperties.apiKey(),
                liveKitProperties.apiSecret(),
                false
        );
        Response<List<LivekitModels.ParticipantInfo>> response =
                roomClient.listParticipants(session.getRoomName()).execute();
        if (!response.isSuccessful() || response.body() == null) {
            return List.of();
        }
        return response.body();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    private record DualIdentities(String agentIdentity, String customerIdentity) {
    }

    @Transactional
    public RecordingResponse stopRecording(String recordingId) {
        RecordingEntity recording = requireRecording(recordingId);
        List<RecordingEntity> group = loadGroupTracks(recording);

        boolean anyMissingEgress = group.stream()
                .anyMatch(r -> r.getEgressId() == null || r.getEgressId().isBlank());
        boolean allTerminal = group.stream().allMatch(this::isTerminal);
        if (allTerminal) {
            return toResponse(recording);
        }
        if (group.size() == 1 && anyMissingEgress) {
            throw new BusinessException(
                    ErrorCode.RECORDING_STOP_FAILED,
                    "Missing egressId. Recording not started yet."
            );
        }

        SessionEntity session = requireSession(recording.getSessionId());
        EgressServiceClient egressClient = createEgressClient();

        try {
            for (RecordingEntity track : group) {
                stopSingleTrack(track, session, egressClient);
            }
            return toResponse(recording);
        } catch (BusinessException e) {
            for (RecordingEntity track : group) {
                if (!isTerminal(track)) {
                    markFailed(track, e.getMessage());
                }
            }
            throw e;
        } catch (IOException e) {
            for (RecordingEntity track : group) {
                if (!isTerminal(track)) {
                    markFailed(track, e.getMessage());
                }
            }
            throw new BusinessException(ErrorCode.RECORDING_STOP_FAILED);
        } catch (RuntimeException e) {
            for (RecordingEntity track : group) {
                if (!isTerminal(track)) {
                    markFailed(track, e.getMessage());
                }
            }
            throw new BusinessException(ErrorCode.RECORDING_STOP_FAILED);
        }
    }

    private void stopSingleTrack(
            RecordingEntity track,
            SessionEntity session,
            EgressServiceClient egressClient
    ) throws IOException {
        if (isTerminal(track)) {
            return;
        }
        if (track.getEgressId() == null || track.getEgressId().isBlank()) {
            markFailed(track, "Missing egressId. Recording not started yet.");
            return;
        }

        Optional<LivekitEgress.EgressInfo> current = findEgressInfo(
                egressClient,
                session.getRoomName(),
                track.getEgressId()
        );
        if (current.isPresent() && RecordingStatusMapper.isTerminalEgressStatus(current.get().getStatus())) {
            applyEgressStatus(
                    track,
                    current.get(),
                    RecordingStatusMapper.fromEgressStatus(current.get().getStatus())
            );
            recordingRepository.save(track);
            log.info("Recording already finished recordingId={} egressStatus={}",
                    track.getId(), current.get().getStatus());
            return;
        }

        track.setStatus(RecordingStatus.STOPPING);
        recordingRepository.save(track);
        log.info("Recording stop requested recordingId={} egressId={}", track.getId(), track.getEgressId());

        Response<LivekitEgress.EgressInfo> response = egressClient.stopEgress(track.getEgressId()).execute();
        if (response.isSuccessful()) {
            return;
        }

        // LiveKit returns 412 when egress is already complete/aborting — treat as idempotent stop.
        if (response.code() == 412) {
            Optional<LivekitEgress.EgressInfo> synced = findEgressInfo(
                    egressClient,
                    session.getRoomName(),
                    track.getEgressId()
            );
            if (synced.isPresent()) {
                applyEgressStatus(
                        track,
                        synced.get(),
                        RecordingStatusMapper.fromEgressStatus(synced.get().getStatus())
                );
                recordingRepository.save(track);
                log.info("Recording stop no-op (412), synced status recordingId={}", track.getId());
                return;
            }
        }

        throw new BusinessException(
                ErrorCode.RECORDING_STOP_FAILED,
                "LiveKit Egress stop failed: HTTP " + response.code(),
                HttpStatus.BAD_GATEWAY
        );
    }

    @Transactional
    public RecordingResponse getRecording(String recordingId) {
        RecordingEntity recording = requireRecording(recordingId);
        List<RecordingEntity> group = loadGroupTracks(recording);
        SessionEntity session = requireSession(recording.getSessionId());

        boolean allTerminal = group.stream().allMatch(this::isTerminal);
        boolean anyNeedsSync = group.stream().anyMatch(r ->
                !isTerminal(r) && r.getEgressId() != null && !r.getEgressId().isBlank()
        );

        if (allTerminal || !anyNeedsSync) {
            return toResponse(recording);
        }

        EgressServiceClient egressClient = createEgressClient();
        try {
            for (RecordingEntity track : group) {
                if (isTerminal(track) || track.getEgressId() == null || track.getEgressId().isBlank()) {
                    continue;
                }
                Optional<LivekitEgress.EgressInfo> egressInfo = findEgressInfo(
                        egressClient,
                        session.getRoomName(),
                        track.getEgressId()
                );
                if (egressInfo.isPresent()) {
                    LivekitEgress.EgressInfo info = egressInfo.get();
                    RecordingStatus newStatus = RecordingStatusMapper.fromEgressStatus(info.getStatus());
                    applyEgressStatus(track, info, newStatus);
                    recordingRepository.save(track);
                    if (newStatus == RecordingStatus.COMPLETED) {
                        log.info("Recording completed recordingId={} objectKey={}",
                                track.getId(), track.getObjectKey());
                    } else if (newStatus == RecordingStatus.FAILED) {
                        log.warn("Recording failed recordingId={}", track.getId());
                    }
                }
            }
        } catch (IOException e) {
            // Keep last known status; polling will retry later.
            return toResponse(recording);
        }

        return toResponse(recording);
    }

    private List<RecordingEntity> loadGroupTracks(RecordingEntity recording) {
        if (recording.getGroupId() != null && !recording.getGroupId().isBlank()) {
            List<RecordingEntity> group = recordingRepository.findByGroupIdOrderBySideAsc(recording.getGroupId());
            if (!group.isEmpty()) {
                return group;
            }
        }
        return List.of(recording);
    }

    private boolean isTerminal(RecordingEntity recording) {
        return recording.getStatus() == RecordingStatus.COMPLETED
                || recording.getStatus() == RecordingStatus.FAILED;
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

    private RecordingResponse toResponse(RecordingEntity requested) {
        List<RecordingEntity> tracks = loadGroupTracks(requested);
        RecordingEntity primary = selectPrimaryTrack(requested, tracks);
        RecordingStatus aggregatedStatus = aggregateStatus(tracks);

        List<RecordingTrackResponse> trackResponses = new ArrayList<>(tracks.size());
        for (RecordingEntity track : tracks) {
            String playbackUrl = track.getStatus() == RecordingStatus.COMPLETED
                    ? presignPlaybackUrl(track.getObjectKey())
                    : null;
            trackResponses.add(new RecordingTrackResponse(
                    track.getId(),
                    track.getSide(),
                    track.getEgressId(),
                    track.getStatus(),
                    track.getObjectKey(),
                    playbackUrl,
                    track.getErrorMessage()
            ));
        }

        String primaryPlaybackUrl = primary.getStatus() == RecordingStatus.COMPLETED
                ? presignPlaybackUrl(primary.getObjectKey())
                : null;

        return new RecordingResponse(
                requested.getId(),
                primary.getSessionId(),
                primary.getEgressId(),
                aggregatedStatus,
                primary.getObjectKey(),
                primaryPlaybackUrl,
                primary.getErrorMessage(),
                primary.getMode() != null ? primary.getMode() : requested.getMode(),
                primary.getGroupId() != null ? primary.getGroupId() : requested.getGroupId(),
                trackResponses
        );
    }

    private RecordingEntity selectPrimaryTrack(RecordingEntity requested, List<RecordingEntity> tracks) {
        Optional<RecordingEntity> composite = tracks.stream()
                .filter(t -> SIDE_COMPOSITE.equals(t.getSide()))
                .findFirst();
        if (composite.isPresent()) {
            return composite.get();
        }
        Optional<RecordingEntity> agent = tracks.stream()
                .filter(t -> SIDE_AGENT.equals(t.getSide()))
                .findFirst();
        if (agent.isPresent()) {
            return agent.get();
        }
        return requested;
    }

    private RecordingStatus aggregateStatus(List<RecordingEntity> tracks) {
        if (tracks.isEmpty()) {
            return RecordingStatus.REQUESTED;
        }
        boolean allTerminal = tracks.stream().allMatch(this::isTerminal);
        boolean anyCompleted = tracks.stream().anyMatch(t -> t.getStatus() == RecordingStatus.COMPLETED);
        boolean anyFailed = tracks.stream().anyMatch(t -> t.getStatus() == RecordingStatus.FAILED);

        // Dual mode: one side can fail (KH chưa vào room) while the other succeeds — treat as COMPLETED.
        if (allTerminal) {
            if (anyCompleted) {
                return RecordingStatus.COMPLETED;
            }
            if (anyFailed) {
                return RecordingStatus.FAILED;
            }
        }
        if (tracks.stream().anyMatch(t -> t.getStatus() == RecordingStatus.STOPPING)) {
            return RecordingStatus.STOPPING;
        }
        if (tracks.stream().anyMatch(t -> t.getStatus() == RecordingStatus.RECORDING)) {
            return RecordingStatus.RECORDING;
        }
        if (tracks.stream().anyMatch(t -> t.getStatus() == RecordingStatus.STARTING)) {
            return RecordingStatus.STARTING;
        }
        return RecordingStatus.REQUESTED;
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
