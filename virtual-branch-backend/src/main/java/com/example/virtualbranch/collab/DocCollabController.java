package com.example.virtualbranch.collab;

import com.example.virtualbranch.collab.dto.ConsentRequest;
import com.example.virtualbranch.collab.dto.DocCollabDocumentUrlResponse;
import com.example.virtualbranch.collab.dto.DocCollabResponse;
import com.example.virtualbranch.collab.dto.StartDocCollabRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class DocCollabController {

    private final DocCollabService docCollabService;

    public DocCollabController(DocCollabService docCollabService) {
        this.docCollabService = docCollabService;
    }

    @PostMapping("/sessions/{sessionId}/doc-collabs")
    public DocCollabResponse startCollab(
            @PathVariable String sessionId,
            @Valid @RequestBody StartDocCollabRequest request
    ) {
        return docCollabService.startCollab(sessionId, request.documentId());
    }

    @GetMapping("/doc-collabs/{collabId}")
    public DocCollabResponse getCollab(@PathVariable String collabId) {
        return docCollabService.getCollab(collabId);
    }

    @PostMapping("/doc-collabs/{collabId}/consent")
    public DocCollabResponse submitConsent(
            @PathVariable String collabId,
            @Valid @RequestBody ConsentRequest request
    ) {
        return docCollabService.submitConsent(collabId, request.toDecision());
    }

    @GetMapping("/doc-collabs/{collabId}/document-url")
    public DocCollabDocumentUrlResponse getDocumentUrl(@PathVariable String collabId) {
        return docCollabService.getCollabDocumentUrl(collabId);
    }

    @PostMapping("/doc-collabs/{collabId}/end")
    public DocCollabResponse endCollab(@PathVariable String collabId) {
        return docCollabService.endCollab(collabId, "AGENT_ENDED");
    }
}
