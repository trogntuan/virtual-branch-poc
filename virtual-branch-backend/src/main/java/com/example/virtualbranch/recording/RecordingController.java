package com.example.virtualbranch.recording;

import com.example.virtualbranch.recording.dto.RecordingResponse;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RecordingController {

    private final RecordingService recordingService;

    public RecordingController(RecordingService recordingService) {
        this.recordingService = recordingService;
    }

    @PostMapping("/sessions/{sessionId}/recordings")
    public RecordingResponse startRecording(@PathVariable String sessionId) {
        return recordingService.startRecording(sessionId);
    }

    @GetMapping("/recordings/{recordingId}")
    public RecordingResponse getRecording(@PathVariable String recordingId) {
        return recordingService.getRecording(recordingId);
    }

    @PostMapping("/recordings/{recordingId}/stop")
    public RecordingResponse stopRecording(@PathVariable String recordingId) {
        return recordingService.stopRecording(recordingId);
    }
}

