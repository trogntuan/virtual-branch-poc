package com.example.virtualbranch.document;

import com.example.virtualbranch.chat.ChatFileValidator;
import com.example.virtualbranch.chat.ChatValidationException;
import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.config.ChatProperties;
import com.example.virtualbranch.document.dto.DocumentResponse;
import com.example.virtualbranch.document.dto.DocumentUrlResponse;
import com.example.virtualbranch.session.SessionEntity;
import com.example.virtualbranch.session.SessionRepository;
import com.example.virtualbranch.session.SessionStatus;
import com.example.virtualbranch.storage.ObjectStorageService;
import com.example.virtualbranch.storage.StorageOperationException;
import java.time.OffsetDateTime;
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
    private static final int READ_URL_EXPIRY_SECONDS = 600;
    private static final String OBJECT_PREFIX = "documents/";

    private final SessionRepository sessionRepository;
    private final DocumentRepository documentRepository;
    private final ObjectStorageService objectStorageService;
    private final ChatFileValidator chatFileValidator;
    private final ChatProperties chatProperties;

    public DocumentService(
            SessionRepository sessionRepository,
            DocumentRepository documentRepository,
            ObjectStorageService objectStorageService,
            ChatFileValidator chatFileValidator,
            ChatProperties chatProperties
    ) {
        this.sessionRepository = sessionRepository;
        this.documentRepository = documentRepository;
        this.objectStorageService = objectStorageService;
        this.chatFileValidator = chatFileValidator;
        this.chatProperties = chatProperties;
    }

    @Transactional
    public DocumentResponse uploadDocument(String sessionId, MultipartFile file) {
        SessionEntity session = requireActiveSession(sessionId);

        validateFile(file);

        String documentId = "DOC-" + UUID.randomUUID();
        String extension = extensionFromFile(file);
        String objectKey = OBJECT_PREFIX + sessionId + "/" + documentId + "." + extension;

        uploadToStorage(objectKey, file);

        String contentType = file.getContentType() != null
                ? file.getContentType()
                : "application/octet-stream";
        DocumentEntity document = new DocumentEntity(
                documentId,
                session.getId(),
                file.getOriginalFilename() != null ? file.getOriginalFilename() : "document." + extension,
                contentType,
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

        try {
            chatFileValidator.validateUpload(file);
        } catch (ChatValidationException exception) {
            if ("FILE_TOO_LARGE".equals(exception.errorCode())) {
                throw new BusinessException(
                        ErrorCode.FILE_TOO_LARGE,
                        "File vượt quá giới hạn " + chatProperties.maxFileSizeLabel() + " cho chat"
                );
            }
            if ("FILE_TYPE_NOT_ALLOWED".equals(exception.errorCode())) {
                throw new BusinessException(
                        ErrorCode.FILE_TYPE_NOT_ALLOWED,
                        "Chỉ chấp nhận file " + chatProperties.allowedExtensionsLabel()
                );
            }
            throw new BusinessException(ErrorCode.INVALID_DOCUMENT, exception.getMessage());
        }
    }

    private static String extensionFromFile(MultipartFile file) {
        String extension = ChatFileValidator.extensionFromFilename(file.getOriginalFilename());
        if (!extension.isBlank()) {
            return extension;
        }
        String contentType = ChatFileValidator.normalizeContentType(file.getContentType());
        return switch (contentType) {
            case "application/pdf" -> "pdf";
            case "image/jpeg" -> "jpg";
            case "image/png" -> "png";
            case "image/gif" -> "gif";
            case "image/webp" -> "webp";
            case "application/msword" -> "doc";
            case "application/vnd.ms-excel" -> "xls";
            case "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" -> "xlsx";
            default -> "bin";
        };
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
            log.warn("Failed to generate read URL for {}: {}", objectKey, e.getMessage());
            throw new BusinessException(
                    ErrorCode.STORAGE_UPLOAD_FAILED,
                    "Failed to generate read URL: " + e.getMessage(),
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
