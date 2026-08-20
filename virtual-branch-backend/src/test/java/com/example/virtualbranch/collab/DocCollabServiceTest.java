package com.example.virtualbranch.collab;

import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
import com.example.virtualbranch.document.DocumentEntity;
import com.example.virtualbranch.document.DocumentRepository;
import com.example.virtualbranch.session.SessionEntity;
import com.example.virtualbranch.session.SessionRepository;
import com.example.virtualbranch.session.SessionStatus;
import com.example.virtualbranch.storage.ObjectStorageService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocCollabServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private DocCollabRepository docCollabRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private DocCollabService docCollabService;

    private SessionEntity session;
    private DocumentEntity document;

    @BeforeEach
    void setUp() {
        session = new SessionEntity("SES-test", "VB-test", SessionStatus.ACTIVE, OffsetDateTime.now());
        document = new DocumentEntity(
                "DOC-test",
                "SES-test",
                "contract.pdf",
                "application/pdf",
                1024,
                "documents/SES-test/DOC-test.pdf",
                null,
                OffsetDateTime.now()
        );
    }

    @Test
    void documentUrlBlockedBeforeConsent() {
        DocCollabEntity collab = new DocCollabEntity(
                "COLLAB-test",
                "SES-test",
                "DOC-test",
                DocCollabStatus.REQUESTED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(docCollabRepository.findById("COLLAB-test")).thenReturn(Optional.of(collab));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> docCollabService.getCollabDocumentUrl("COLLAB-test")
        );

        assertEquals(ErrorCode.COLLAB_CONSENT_REQUIRED, exception.getErrorCode());
    }

    @Test
    void acceptActivatesCollab() {
        DocCollabEntity collab = new DocCollabEntity(
                "COLLAB-test",
                "SES-test",
                "DOC-test",
                DocCollabStatus.REQUESTED,
                OffsetDateTime.now(),
                OffsetDateTime.now()
        );

        when(docCollabRepository.findById("COLLAB-test")).thenReturn(Optional.of(collab));

        var response = docCollabService.submitConsent("COLLAB-test", ConsentDecision.ACCEPT);

        assertEquals(DocCollabStatus.ACTIVE, response.status());
        assertEquals(ConsentDecision.ACCEPT, response.consentDecision());
    }

    @Test
    void startCollabRequiresDocumentInSession() {
        when(sessionRepository.findById("SES-test")).thenReturn(Optional.of(session));
        when(documentRepository.findById("DOC-other")).thenReturn(Optional.of(
                new DocumentEntity(
                        "DOC-other",
                        "SES-other",
                        "other.pdf",
                        "application/pdf",
                        100,
                        "documents/SES-other/DOC-other.pdf",
                        null,
                        OffsetDateTime.now()
                )
        ));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> docCollabService.startCollab("SES-test", "DOC-other")
        );

        assertEquals(ErrorCode.DOCUMENT_NOT_FOUND, exception.getErrorCode());
        verify(docCollabRepository, never()).save(any());
    }
}
