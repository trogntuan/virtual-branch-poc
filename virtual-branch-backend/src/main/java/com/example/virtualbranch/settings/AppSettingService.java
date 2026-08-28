package com.example.virtualbranch.settings;

import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.settings.dto.RecordingModeSettingResponse;
import com.example.virtualbranch.settings.dto.RecordingSettingResponse;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSettingService {

    public static final String RECORDING_ENABLED_KEY = "recording.enabled";
    public static final String RECORDING_MODE_KEY = "recording.mode";

    private static final Logger log = LoggerFactory.getLogger(AppSettingService.class);

    private final AppSettingRepository appSettingRepository;

    public AppSettingService(AppSettingRepository appSettingRepository) {
        this.appSettingRepository = appSettingRepository;
    }

    @Transactional(readOnly = true)
    public boolean isRecordingEnabled() {
        return appSettingRepository.findById(RECORDING_ENABLED_KEY)
                .map(row -> parseBoolean(row.getValue()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public RecordingSettingResponse getRecordingSetting() {
        return new RecordingSettingResponse(isRecordingEnabled());
    }

    @Transactional
    public RecordingSettingResponse setRecordingEnabled(boolean enabled) {
        AppSettingEntity row = appSettingRepository.findById(RECORDING_ENABLED_KEY)
                .orElseGet(() -> new AppSettingEntity(
                        RECORDING_ENABLED_KEY,
                        String.valueOf(enabled),
                        OffsetDateTime.now()
                ));
        row.setValue(String.valueOf(enabled));
        row.setUpdatedAt(OffsetDateTime.now());
        appSettingRepository.save(row);
        log.info("App setting updated key={} value={}", RECORDING_ENABLED_KEY, enabled);
        return new RecordingSettingResponse(enabled);
    }

    @Transactional(readOnly = true)
    public RecordingMode getRecordingMode() {
        return appSettingRepository.findById(RECORDING_MODE_KEY)
                .map(row -> {
                    RecordingMode mode = RecordingMode.fromSetting(row.getValue());
                    return mode != null ? mode : RecordingMode.ROOM_COMPOSITE;
                })
                .orElse(RecordingMode.ROOM_COMPOSITE);
    }

    @Transactional(readOnly = true)
    public RecordingModeSettingResponse getRecordingModeSetting() {
        return new RecordingModeSettingResponse(getRecordingMode().name());
    }

    @Transactional
    public RecordingModeSettingResponse setRecordingMode(String rawMode) {
        RecordingMode mode;
        try {
            mode = RecordingMode.requireFromSetting(rawMode);
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST, ex.getMessage(), HttpStatus.BAD_REQUEST);
        }

        AppSettingEntity row = appSettingRepository.findById(RECORDING_MODE_KEY)
                .orElseGet(() -> new AppSettingEntity(
                        RECORDING_MODE_KEY,
                        mode.name(),
                        OffsetDateTime.now()
                ));
        row.setValue(mode.name());
        row.setUpdatedAt(OffsetDateTime.now());
        appSettingRepository.save(row);
        log.info("App setting updated key={} value={}", RECORDING_MODE_KEY, mode);
        return new RecordingModeSettingResponse(mode.name());
    }

    private static boolean parseBoolean(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.trim().toLowerCase();
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
    }
}
