package com.example.virtualbranch.collab;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocCollabRepository extends JpaRepository<DocCollabEntity, String> {

    List<DocCollabEntity> findBySessionIdOrderByStartedAtDesc(String sessionId);

    Optional<DocCollabEntity> findFirstBySessionIdAndStatusInOrderByStartedAtDesc(
            String sessionId,
            List<DocCollabStatus> statuses
    );
}
