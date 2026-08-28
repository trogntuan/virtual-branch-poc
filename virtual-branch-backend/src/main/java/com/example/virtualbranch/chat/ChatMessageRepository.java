package com.example.virtualbranch.chat;

import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatMessageRepository extends JpaRepository<ChatMessageEntity, String> {

    @Query("""
            SELECT m FROM ChatMessageEntity m
            WHERE m.sessionId = :sessionId
            ORDER BY m.sentAt ASC, m.id ASC
            """)
    List<ChatMessageEntity> findHistory(@Param("sessionId") String sessionId, Pageable pageable);

    @Query("""
            SELECT m FROM ChatMessageEntity m
            WHERE m.sessionId = :sessionId
              AND (m.sentAt > :afterSentAt
                   OR (m.sentAt = :afterSentAt AND m.id > :afterId))
            ORDER BY m.sentAt ASC, m.id ASC
            """)
    List<ChatMessageEntity> findAfter(
            @Param("sessionId") String sessionId,
            @Param("afterSentAt") OffsetDateTime afterSentAt,
            @Param("afterId") String afterId,
            Pageable pageable);
}
