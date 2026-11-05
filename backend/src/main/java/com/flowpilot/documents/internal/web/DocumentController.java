package com.flowpilot.documents.internal.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.documents.internal.application.DocumentAccessGuard;
import com.flowpilot.documents.internal.application.DocumentService;
import com.flowpilot.documents.internal.domain.Document;
import com.flowpilot.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/documents")
class DocumentController {

    private static final int MAX_PAGE_SIZE = 100;

    private final DocumentService documents;
    private final DocumentAccessGuard access;
    private final AuditTrail audit;

    DocumentController(DocumentService documents, DocumentAccessGuard access, AuditTrail audit) {
        this.documents = documents;
        this.access = access;
        this.audit = audit;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentResponse> upload(@PathVariable UUID orgId,
                                            @PathVariable UUID projectId,
                                            @RequestParam("file") MultipartFile file,
                                            @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        access.requireWrite(user.userId(), orgId, projectId);
        Document document;
        try (InputStream content = file.getInputStream()) {
            document = documents.upload(orgId, projectId, user.userId(), file.getOriginalFilename(), content);
        }
        audit.record(AuditEvent.success(AuditActions.DOCUMENT_UPLOADED, orgId, user.userId())
                .withTarget("document", document.getId())
                .withDetail("projectId", projectId.toString())
                .withDetail("sizeBytes", document.getSizeBytes()));
        URI location = URI.create("/api/v1/orgs/" + orgId + "/projects/" + projectId
                + "/documents/" + document.getId());
        return ResponseEntity.created(location).body(DocumentResponse.from(document));
    }

    @GetMapping
    PagedModel<DocumentResponse> list(@PathVariable UUID orgId,
                                      @PathVariable UUID projectId,
                                      @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "20") int size,
                                      @AuthenticationPrincipal AuthenticatedUser user) {
        access.requireRead(user.userId(), orgId, projectId);
        PageRequest request = PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), MAX_PAGE_SIZE),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<DocumentResponse> result = documents.list(projectId, request).map(DocumentResponse::from);
        return new PagedModel<>(result);
    }

    @GetMapping("/{documentId}")
    DocumentResponse get(@PathVariable UUID orgId,
                         @PathVariable UUID projectId,
                         @PathVariable UUID documentId,
                         @AuthenticationPrincipal AuthenticatedUser user) {
        access.requireRead(user.userId(), orgId, projectId);
        return DocumentResponse.from(documents.get(projectId, documentId));
    }
}