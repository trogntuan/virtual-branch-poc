package com.example.virtualbranch.collab;

import com.example.virtualbranch.collab.dto.DocCollabDocumentUrlResponse;
import com.example.virtualbranch.collab.dto.DocCollabResponse;
import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.document.DocumentEntity;
import com.example.virtualbranch.document.DocumentRepository;
import com.example.virtualbranch.session.SessionEntity;
import com.example.virtualbranch.session.SessionRepository;
import com.example.virtualbranch.session.SessionStatus;
import com.example.virtualbranch.storage.ObjectStorageService;
import com.example.virtualbranch.storage.StorageOperationException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocCollabService {

    private static final Logger log = LoggerFactory.getLogger(DocCollabService.class);
    private static final int READ_URL_EXPIRY_SECONDS = 600;

    private final SessionRepository sessionRepository;
    private final DocumentRepository documentRepository;
    private final DocCollabRepository docCollabRepository;
    private final ObjectStorageService objectStorageService;

    public DocCollabService(
            SessionRepository sessionRepository,
            DocumentRepository documentRepository,
            DocCollabRepository docCollabRepository,
            ObjectStorageService objectStorageService
    ) {
        this.sessionRepository = sessionRepository;
        this.documentRepository = documentRepository;
        this.docCollabRepository = docCollabRepository;
        this.objectStorageService = objectStorageService;
    }

    @Transactional
    public DocCollabResponse startCollab(String sessionId, String documentId) {
        SessionEntity session = requireActiveSession(sessionId);
        DocumentEntity document = requireDocumentForSession(documentId, sessionId);

        docCollabRepository.findFirstBySessionIdAndStatusInOrderByStartedAtDesc(
                sessionId,
                List.of(DocCollabStatus.REQUESTED, DocCollabStatus.ACTIVE)
        ).ifPresent(existing -> {
            throw new BusinessException(
                    ErrorCode.COLLAB_INVALID_STATE,
                    "An active or pending collab already exists for this session",
                    HttpStatus.CONFLICT
            );
        });

        OffsetDateTime now = OffsetDateTime.now();
        DocCollabEntity collab = new DocCollabEntity(
                "COLLAB-" + UUID.randomUUID(),
                session.getId(),
                document.getId(),
                DocCollabStatus.REQUESTED,
                now,
                now
        );
        docCollabRepository.save(collab);
        log.info("Doc collab requested sessionId={} collabId={} documentId={}",
                sessionId, collab.getId(), documentId);

        return toResponse(collab);
    }

    @Transactional
    public DocCollabResponse submitConsent(String collabId, ConsentDecision decision) {
        if (decision == null) {
            throw new BusinessException(ErrorCode.INVALID_CONSENT);
        }

        DocCollabEntity collab = requireCollab(collabId);
        if (collab.getStatus() != DocCollabStatus.REQUESTED) {
            throw new BusinessException(ErrorCode.COLLAB_INVALID_STATE);
        }

        OffsetDateTime now = OffsetDateTime.now();
        collab.setConsentDecision(decision);
        collab.setConsentAt(now);

        if (decision == ConsentDecision.ACCEPT) {
            collab.setStatus(DocCollabStatus.ACTIVE);
            log.info("Doc collab accepted collabId={}", collabId);
        } else {
            collab.setStatus(DocCollabStatus.REJECTED);
            collab.setEndedAt(now);
            collab.setEndReason("CUSTOMER_REJECTED");
            log.info("Doc collab rejected collabId={}", collabId);
        }

        return toResponse(collab);
    }

    @Transactional(readOnly = true)
    public DocCollabResponse getCollab(String collabId) {
        return toResponse(requireCollab(collabId));
    }

    @Transactional(readOnly = true)
    public DocCollabDocumentUrlResponse getCollabDocumentUrl(String collabId) {
        DocCollabEntity collab = requireCollab(collabId);

        if (collab.getStatus() != DocCollabStatus.ACTIVE) {
            throw new BusinessException(
                    ErrorCode.COLLAB_CONSENT_REQUIRED,
                    "Collab is not active",
                    HttpStatus.FORBIDDEN
            );
        }
        if (collab.getConsentDecision() != ConsentDecision.ACCEPT) {
            throw new BusinessException(
                    ErrorCode.COLLAB_CONSENT_REQUIRED,
                    "Customer consent required before document access",
                    HttpStatus.FORBIDDEN
            );
        }

        DocumentEntity document = requireDocumentForSession(collab.getDocumentId(), collab.getSessionId());
        String readUrl = presignReadUrl(document.getObjectKey());

        return new DocCollabDocumentUrlResponse(document.getId(), readUrl, READ_URL_EXPIRY_SECONDS);
    }

    @Transactional
    public DocCollabResponse endCollab(String collabId, String reason) {
        DocCollabEntity collab = requireCollab(collabId);
        if (collab.getStatus() == DocCollabStatus.ENDED || collab.getStatus() == DocCollabStatus.REJECTED) {
            return toResponse(collab);
        }

        collab.setStatus(DocCollabStatus.ENDED);
        collab.setEndedAt(OffsetDateTime.now());
        collab.setEndReason(reason != null ? reason : "AGENT_ENDED");
        log.info("Doc collab ended collabId={} reason={}", collabId, collab.getEndReason());

        return toResponse(collab);
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

    private DocumentEntity requireDocumentForSession(String documentId, String sessionId) {
        DocumentEntity document = documentRepository.findById(documentId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.DOCUMENT_NOT_FOUND,
                        ErrorCode.DOCUMENT_NOT_FOUND.getDefaultMessage(),
                        HttpStatus.NOT_FOUND
                ));
        if (!document.getSessionId().equals(sessionId)) {
            throw new BusinessException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        return document;
    }

    private DocCollabEntity requireCollab(String collabId) {
        return docCollabRepository.findById(collabId)
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.COLLAB_NOT_FOUND,
                        ErrorCode.COLLAB_NOT_FOUND.getDefaultMessage(),
                        HttpStatus.NOT_FOUND
                ));
    }

    private DocCollabResponse toResponse(DocCollabEntity collab) {
        return new DocCollabResponse(
                collab.getId(),
                collab.getSessionId(),
                collab.getDocumentId(),
                collab.getStatus(),
                collab.getConsentDecision()
        );
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
}
