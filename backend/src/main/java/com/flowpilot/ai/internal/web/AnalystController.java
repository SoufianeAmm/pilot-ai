package com.flowpilot.ai.internal.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowpilot.ai.internal.application.AnalystService;
import com.flowpilot.ai.internal.application.ConversationService;
import com.flowpilot.ai.internal.application.ConversationView;
import com.flowpilot.ai.internal.application.MessageView;
import com.flowpilot.projects.ProjectAccess;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.shared.security.AuthenticatedUser;

/**
 * The AI analyst of a project. Every endpoint resolves the ProjectScope first: someone outside the
 * organization or the project gets 404, exactly as for a project that does not exist.
 */
@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/ai/conversations")
class AnalystController {

    record CreateConversationRequest(String title) {
    }

    record AskRequest(String question) {
    }

    private final ProjectAccess projectAccess;
    private final ConversationService conversations;
    private final AnalystService analyst;

    AnalystController(ProjectAccess projectAccess, ConversationService conversations, AnalystService analyst) {
        this.projectAccess = projectAccess;
        this.conversations = conversations;
        this.analyst = analyst;
    }

    @PostMapping
    ResponseEntity<ConversationView> create(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                            @RequestBody(required = false) CreateConversationRequest request,
                                            @AuthenticationPrincipal AuthenticatedUser user) {
        ConversationView created = conversations.create(scope(orgId, projectId, user),
                request == null ? null : request.title());
        return ResponseEntity.created(URI.create("/api/v1/orgs/" + orgId + "/projects/" + projectId
                + "/ai/conversations/" + created.id())).body(created);
    }

    @GetMapping
    List<ConversationView> list(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        return conversations.list(scope(orgId, projectId, user));
    }

    @GetMapping("/{conversationId}/messages")
    List<MessageView> messages(@PathVariable UUID orgId, @PathVariable UUID projectId,
                               @PathVariable UUID conversationId,
                               @AuthenticationPrincipal AuthenticatedUser user) {
        return conversations.messages(scope(orgId, projectId, user), conversationId);
    }

    /** Asks a question; the answer comes back in the response (it can take several seconds). */
    @PostMapping("/{conversationId}/messages")
    MessageView ask(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID conversationId,
                    @RequestBody(required = false) AskRequest request,
                    @AuthenticationPrincipal AuthenticatedUser user) {
        return analyst.ask(scope(orgId, projectId, user), conversationId, request == null ? null : request.question());
    }

    @DeleteMapping("/{conversationId}")
    ResponseEntity<Void> delete(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                @PathVariable UUID conversationId,
                                @AuthenticationPrincipal AuthenticatedUser user) {
        conversations.delete(scope(orgId, projectId, user), conversationId);
        return ResponseEntity.noContent().build();
    }

    private ProjectScope scope(UUID orgId, UUID projectId, AuthenticatedUser user) {
        return projectAccess.requirePermission(orgId, projectId, user.userId(), ProjectPermission.AI_ASK);
    }
}
