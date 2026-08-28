package com.example.virtualbranch.recording;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecordingRepository extends JpaRepository<RecordingEntity, String> {
    List<RecordingEntity> findByGroupIdOrderBySideAsc(String groupId);
}
