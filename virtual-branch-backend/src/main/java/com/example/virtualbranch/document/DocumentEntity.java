package com.example.virtualbranch.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

@Entity
@Table(name = "vb_document")
public class DocumentEntity {

    @Id
    private String id;

    @Column(name = "session_id", nullable = false)
    private String sessionId;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected DocumentEntity() {
    }

    public DocumentEntity(String id, String sessionId, String fileName, String contentType,
                          long fileSize, String objectKey, String checksum, OffsetDateTime createdAt) {
        this.id = id;
        this.sessionId = sessionId;
        this.fileName = fileName;
        this.contentType = contentType;
        this.fileSize = fileSize;
        this.objectKey = objectKey;
        this.checksum = checksum;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getSessionId() { return sessionId; }
    public String getFileName() { return fileName; }
    public String getContentType() { return contentType; }
    public long getFileSize() { return fileSize; }
    public String getObjectKey() { return objectKey; }
    public String getChecksum() { return checksum; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
