package com.flowpilot.ai.internal.web;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.flowpilot.ai.internal.application.AiAccessGuard;
import com.flowpilot.ai.internal.persistence.ConversationStore;
import com.flowpilot.ai.internal.persistence.ConversationStore.Conversation;
import com.flowpilot.ai.internal.persistence.ConversationStore.Message;
import com.flowpilot.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/ai/conversations")
class ConversationController {

    private static final int MAX_TITLE_LENGTH = 200;
    private static final String DEFAULT_TITLE = "Nouvelle conversation";

    record CreateRequest(String title) {
    }

    private final ConversationStore store;
    private final AiAccessGuard access;

    ConversationController(ConversationStore store, AiAccessGuard access) {
        this.store = store;
        this.access = access;
    }

    @PostMapping
    ResponseEntity<Conversation> create(@PathVariable UUID orgId, @PathVariable UUID projectId,
                                        @RequestBody(required = false) CreateRequest request,
                                        @AuthenticationPrincipal AuthenticatedUser user) {
        access.requireWrite(user.userId(), orgId, projectId);
        String title = request == null || request.title() == null ? "" : request.title().strip();
        if (title.isEmpty()) {
            title = DEFAULT_TITLE;
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The title must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        Conversation created = store.create(orgId, projectId, user.userId(), title);
        URI location = URI.create("/api/v1/orgs/" + orgId + "/projects/" + projectId
                + "/ai/conversations/" + created.id());
        return ResponseEntity.created(location).body(created);
    }

    @GetMapping
    List<Conversation> list(@PathVariable UUID orgId, @PathVariable UUID projectId,
                            @AuthenticationPrincipal AuthenticatedUser user) {
        access.requireRead(user.userId(), orgId, projectId);
        return store.list(projectId, user.userId());
    }

    @GetMapping("/{conversationId}/messages")
    List<Message> messages(@PathVariable UUID orgId, @PathVariable UUID projectId,
                           @PathVariable UUID conversationId,
                           @AuthenticationPrincipal AuthenticatedUser user) {
        access.requireRead(user.userId(), orgId, projectId);
        store.find(projectId, user.userId(), conversationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found."));
        return store.messages(conversationId);
    }
}