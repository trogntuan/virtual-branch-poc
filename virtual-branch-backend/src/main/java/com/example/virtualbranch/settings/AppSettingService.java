package com.example.virtualbranch.settings;

import com.example.virtualbranch.settings.dto.RecordingSettingResponse;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AppSettingService {

    public static final String RECORDING_ENABLED_KEY = "recording.enabled";

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

    private static boolean parseBoolean(String raw) {
        if (raw == null) {
            return false;
        }
        String normalized = raw.trim().toLowerCase();
        return "true".equals(normalized) || "1".equals(normalized) || "yes".equals(normalized);
    }
}
