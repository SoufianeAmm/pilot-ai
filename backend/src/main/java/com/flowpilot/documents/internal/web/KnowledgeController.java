package com.flowpilot.documents.internal.web;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.flowpilot.documents.internal.application.DocumentAccessGuard;
import com.flowpilot.documents.internal.ingestion.KnowledgeSearch;
import com.flowpilot.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/knowledge")
class KnowledgeController {

    private static final int MAX_QUESTION_LENGTH = 1000;
    private static final int DEFAULT_LIMIT = 5;

    record QueryRequest(String question, Integer limit) {
    }

    /** One passage with the citation needed to show where it comes from. */
    record PassageResponse(UUID chunkId, Citation citation, String content, double score) {
    }

    record Citation(UUID documentId, String filename, Integer pageFrom, Integer pageTo) {
    }

    record QueryResponse(String question, List<PassageResponse> results) {
    }

    private final KnowledgeSearch search;
    private final DocumentAccessGuard access;

    KnowledgeController(KnowledgeSearch search, DocumentAccessGuard access) {
        this.search = search;
        this.access = access;
    }

    @PostMapping("/query")
    QueryResponse query(@PathVariable UUID orgId,
                        @PathVariable UUID projectId,
                        @RequestBody QueryRequest request,
                        @AuthenticationPrincipal AuthenticatedUser user) {
        access.requireRead(user.userId(), orgId, projectId);

        String question = request.question() == null ? "" : request.question().strip();
        if (question.isEmpty() || question.length() > MAX_QUESTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The question must be between 1 and " + MAX_QUESTION_LENGTH + " characters.");
        }
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();

        List<PassageResponse> results = search.search(projectId, question, limit).stream()
                .map(p -> new PassageResponse(
                        p.chunkId(),
                        new Citation(p.documentId(), p.filename(), p.pageFrom(), p.pageTo()),
                        p.content(),
                        p.score()))
                .toList();
        return new QueryResponse(question, results);
    }
}