package com.example.virtualbranch.settings;

import com.example.virtualbranch.settings.dto.RecordingModeSettingResponse;
import com.example.virtualbranch.settings.dto.RecordingSettingResponse;
import com.example.virtualbranch.settings.dto.UpdateRecordingModeSettingRequest;
import com.example.virtualbranch.settings.dto.UpdateRecordingSettingRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/settings")
public class SettingsController {

    private final AppSettingService appSettingService;

    public SettingsController(AppSettingService appSettingService) {
        this.appSettingService = appSettingService;
    }

    @GetMapping("/recording")
    public RecordingSettingResponse getRecordingSetting() {
        return appSettingService.getRecordingSetting();
    }

    @PutMapping("/recording")
    public RecordingSettingResponse updateRecordingSetting(
            @Valid @RequestBody UpdateRecordingSettingRequest request
    ) {
        return appSettingService.setRecordingEnabled(Boolean.TRUE.equals(request.enabled()));
    }

    @GetMapping("/recording-mode")
    public RecordingModeSettingResponse getRecordingMode() {
        return appSettingService.getRecordingModeSetting();
    }

    @PutMapping("/recording-mode")
    public RecordingModeSettingResponse updateRecordingMode(
            @Valid @RequestBody UpdateRecordingModeSettingRequest request
    ) {
        return appSettingService.setRecordingMode(request.mode());
    }
}
