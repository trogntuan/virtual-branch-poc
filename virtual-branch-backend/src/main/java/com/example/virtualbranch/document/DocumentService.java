package com.example.virtualbranch.document;

import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.document.dto.DocumentResponse;
import com.example.virtualbranch.document.dto.DocumentUrlResponse;
import com.example.virtualbranch.session.SessionEntity;
import com.example.virtualbranch.session.SessionRepository;
import com.example.virtualbranch.session.SessionStatus;
import com.example.virtualbranch.storage.ObjectStorageService;
import com.example.virtualbranch.storage.StorageOperationException;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final Logger log = LoggerFactory.getLogger(DocumentService.class);
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("application/pdf");
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024; // 50 MB
    private static final int READ_URL_EXPIRY_SECONDS = 600;
    private static final String OBJECT_PREFIX = "documents/";

    private final SessionRepository sessionRepository;
    private final DocumentRepository documentRepository;
    private final ObjectStorageService objectStorageService;

    public DocumentService(SessionRepository sessionRepository,
                           DocumentRepository documentRepository,
                           ObjectStorageService objectStorageService) {
        this.sessionRepository = sessionRepository;
        this.documentRepository = documentRepository;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public DocumentResponse uploadDocument(String sessionId, MultipartFile file) {
        SessionEntity session = requireActiveSession(sessionId);

        validateFile(file);

        String documentId = "DOC-" + UUID.randomUUID();
        String objectKey = OBJECT_PREFIX + sessionId + "/" + documentId + ".pdf";

        uploadToStorage(objectKey, file);

        DocumentEntity document = new DocumentEntity(
                documentId,
                session.getId(),
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "document.pdf",
                file.getContentType() != null ? file.getContentType() : "application/pdf",
                file.getSize(),
                objectKey,
                null,
                OffsetDateTime.now()
        );
        documentRepository.save(document);
        log.info("Document uploaded sessionId={} documentId={} size={}", sessionId, documentId, file.getSize());

        return toResponse(document, presignReadUrl(objectKey));
    }

    @Transactional(readOnly = true)
    public DocumentUrlResponse getDocumentUrl(String documentId) {
        DocumentEntity document = requireDocument(documentId);
        String readUrl = presignReadUrl(document.getObjectKey());
        return new DocumentUrlResponse(document.getId(), readUrl, READ_URL_EXPIRY_SECONDS);
    }

    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT, "File is empty");
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType)) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT,
                    "Only PDF files are allowed. Got: " + contentType);
        }

        String originalName = file.getOriginalFilename();
        if (originalName != null && !originalName.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT,
                    "File extension must be .pdf");
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT,
                    "File size exceeds maximum of 50 MB");
        }
    }

    private void uploadToStorage(String objectKey, MultipartFile file) {
        try {
            objectStorageService.upload(
                    objectKey,
                    file.getInputStream(),
                    file.getSize(),
                    file.getContentType()
            );
        } catch (StorageOperationException e) {
            throw new BusinessException(
                    ErrorCode.STORAGE_UPLOAD_FAILED,
                    "Failed to upload document to storage: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        } catch (Exception e) {
            throw new BusinessException(
                    ErrorCode.STORAGE_UPLOAD_FAILED,
                    "Failed to upload document to storage: " + e.getMessage(),
                    HttpStatus.BAD_GATEWAY
            );
        }
    }

    private String presignReadUrl(String objectKey) {
        try {
            return objectStorageService.presignGetUrl(objectKey, READ_URL_EXPIRY_SECONDS);
        } catch (StorageOperationException e) {
            throw new BusinessException(
                    ErrorCode.STORAGE_UPLOAD_FAILED,
                    "Failed to generate read URL",
                    HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
    }

    private SessionEntity requireActiveSession(String sessionId) {
        SessionEntity session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.SESSION_NOT_FOUND,
                        ErrorCode.SESSION_NOT_FOUND.getDefaultMessage(),
                        HttpStatus.NOT_FOUND
                ));
        if (session.getStatus() == SessionStatus.ENDED) {
            throw new BusinessException(ErrorCode.SESSION_ENDED);
        }
        if (session.getStatus() == SessionStatus.WAITING) {
            throw new BusinessException(ErrorCode.CALL_NOT_ACCEPTED);
        }
        return session;
    }

    private DocumentEntity requireDocument(String documentId) {
        return documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DOCUMENT_NOT_FOUND,
                        ErrorCode.DOCUMENT_NOT_FOUND.getDefaultMessage(),
                        HttpStatus.NOT_FOUND
                ));
    }

    private DocumentResponse toResponse(DocumentEntity doc, String readUrl) {
        return new DocumentResponse(
                doc.getId(),
                doc.getFileName(),
                doc.getContentType(),
                doc.getFileSize(),
                readUrl
        );
    }
}
