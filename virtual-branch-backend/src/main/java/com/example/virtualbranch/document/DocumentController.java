package com.example.virtualbranch.document;

import com.example.virtualbranch.document.dto.DocumentResponse;
import com.example.virtualbranch.document.dto.DocumentUrlResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping("/sessions/{sessionId}/documents")
    public DocumentResponse uploadDocument(
            @PathVariable String sessionId,
            @RequestParam("file") MultipartFile file
    ) {
        return documentService.uploadDocument(sessionId, file);
    }

    @GetMapping("/documents/{documentId}/url")
    public DocumentUrlResponse getDocumentUrl(@PathVariable String documentId) {
        return documentService.getDocumentUrl(documentId);
    }
}
