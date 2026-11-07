package com.flowpilot.documents.internal.web;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.flowpilot.audit.AuditActions;
import com.flowpilot.audit.AuditEvent;
import com.flowpilot.audit.AuditTrail;
import com.flowpilot.documents.internal.application.DocumentService;
import com.flowpilot.documents.internal.domain.Document;
import com.flowpilot.projects.ProjectAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.shared.security.AuthenticatedUser;
import com.flowpilot.shared.web.PageResponse;
import com.flowpilot.shared.web.Pageables;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/documents")
class DocumentController {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> SORT_WHITELIST = Set.of("filename", "status", "sizeBytes", "createdAt");
    private static final Sort DEFAULT_SORT = Sort.by("createdAt").descending();

    private final DocumentService documents;
    private final ProjectAccess projectAccess;
    private final AuditTrail audit;

    DocumentController(DocumentService documents, ProjectAccess projectAccess, AuditTrail audit) {
        this.documents = documents;
        this.projectAccess = projectAccess;
        this.audit = audit;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentResponse> upload(@PathVariable UUID orgId,
                                            @PathVariable UUID projectId,
                                            @RequestParam("file") MultipartFile file,
                                            @AuthenticationPrincipal AuthenticatedUser user) throws IOException {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.DOCUMENT_UPLOAD);
        Document document;
        try (InputStream content = file.getInputStream()) {
            document = documents.upload(scope, file.getOriginalFilename(), content);
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
    PageResponse<DocumentResponse> list(@PathVariable UUID orgId,
                                        @PathVariable UUID projectId,
                                        Pageable pageable,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.PROJECT_READ);
        Pageable restricted = Pageables.restrictSort(pageable, SORT_WHITELIST, DEFAULT_SORT);
        Pageable safe = PageRequest.of(restricted.getPageNumber(),
                Math.min(restricted.getPageSize(), MAX_PAGE_SIZE), restricted.getSort());
        return PageResponse.of(documents.list(scope, safe), DocumentResponse::from);
    }

    @GetMapping("/{documentId}")
    DocumentResponse get(@PathVariable UUID orgId,
                         @PathVariable UUID projectId,
                         @PathVariable UUID documentId,
                         @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.PROJECT_READ);
        return DocumentResponse.from(documents.get(scope, documentId));
    }

    @GetMapping("/{documentId}/content")
    ResponseEntity<StreamingResponseBody> content(@PathVariable UUID orgId,
                                                  @PathVariable UUID projectId,
                                                  @PathVariable UUID documentId,
                                                  @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.PROJECT_READ);
        Document document = documents.get(scope, documentId);
        StreamingResponseBody body = out -> {
            try (InputStream in = documents.openContent(document)) {
                in.transferTo(out);
            }
        };
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(document.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline()
                        .filename(document.getFilename(), StandardCharsets.UTF_8).build().toString())
                .body(body);
    }

    @DeleteMapping("/{documentId}")
    ResponseEntity<Void> delete(@PathVariable UUID orgId,
                                @PathVariable UUID projectId,
                                @PathVariable UUID documentId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        // membership is enough here: the service decides "any document" (manager) or "own document"
        ProjectScope scope = projectAccess.requireMembership(orgId, projectId, user.userId());
        Document document = documents.delete(scope, documentId);
        audit.record(AuditEvent.success(AuditActions.DOCUMENT_DELETED, orgId, user.userId())
                .withTarget("document", document.getId())
                .withDetail("projectId", projectId.toString()));
        return ResponseEntity.noContent().build();
    }
}
