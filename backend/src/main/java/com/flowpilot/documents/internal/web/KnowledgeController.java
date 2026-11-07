package com.flowpilot.documents.internal.web;

import java.util.List;
import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.documents.KnowledgeApi;
import com.flowpilot.projects.ProjectAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.shared.security.AuthenticatedUser;
import com.flowpilot.shared.web.ApiException;

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

    private final KnowledgeApi knowledge;
    private final ProjectAccess projectAccess;

    KnowledgeController(KnowledgeApi knowledge, ProjectAccess projectAccess) {
        this.knowledge = knowledge;
        this.projectAccess = projectAccess;
    }

    @PostMapping("/query")
    QueryResponse query(@PathVariable UUID orgId,
                        @PathVariable UUID projectId,
                        @RequestBody QueryRequest request,
                        @AuthenticationPrincipal AuthenticatedUser user) {
        ProjectScope scope = projectAccess.requirePermission(orgId, projectId, user.userId(),
                ProjectPermission.PROJECT_READ);

        String question = request.question() == null ? "" : request.question().strip();
        if (question.isEmpty() || question.length() > MAX_QUESTION_LENGTH) {
            throw ApiException.badRequest("The question must be between 1 and " + MAX_QUESTION_LENGTH + " characters.");
        }
        int limit = request.limit() == null ? DEFAULT_LIMIT : request.limit();

        List<PassageResponse> results = knowledge.search(scope, question, limit).stream()
                .map(p -> new PassageResponse(
                        p.chunkId(),
                        new Citation(p.documentId(), p.filename(), p.pageFrom(), p.pageTo()),
                        p.content(),
                        p.score()))
                .toList();
        return new QueryResponse(question, results);
    }
}
