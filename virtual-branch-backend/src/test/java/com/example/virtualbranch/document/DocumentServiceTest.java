package com.example.virtualbranch.document;

import com.example.virtualbranch.common.BusinessException;
import com.example.virtualbranch.common.ErrorCode;
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
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ObjectStorageService objectStorageService;

    @InjectMocks
    private DocumentService documentService;

    private SessionEntity activeSession;

    @BeforeEach
    void setUp() {
        activeSession = new SessionEntity(
                "SES-test",
                "VB-test",
                SessionStatus.ACTIVE,
                OffsetDateTime.now()
        );
    }

    @Test
    void rejectsNonPdfContentType() {
        when(sessionRepository.findById("SES-test")).thenReturn(Optional.of(activeSession));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "hello".getBytes()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentService.uploadDocument("SES-test", file)
        );

        assertEquals(ErrorCode.INVALID_DOCUMENT, exception.getErrorCode());
        verify(documentRepository, never()).save(any());
    }

    @Test
    void rejectsEndedSession() {
        SessionEntity endedSession = new SessionEntity(
                "SES-ended",
                "VB-ended",
                SessionStatus.ENDED,
                OffsetDateTime.now()
        );
        when(sessionRepository.findById("SES-ended")).thenReturn(Optional.of(endedSession));

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "%PDF-1.4".getBytes()
        );

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> documentService.uploadDocument("SES-ended", file)
        );

        assertEquals(ErrorCode.SESSION_ENDED, exception.getErrorCode());
    }
}
