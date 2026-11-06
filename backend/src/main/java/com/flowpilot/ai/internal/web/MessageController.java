package com.flowpilot.ai.internal.web;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.flowpilot.ai.internal.application.AiAccessGuard;
import com.flowpilot.ai.internal.application.AssistantService;
import com.flowpilot.ai.internal.persistence.ConversationStore.Message;
import com.flowpilot.shared.security.AuthenticatedUser;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/projects/{projectId}/ai/conversations/{conversationId}/messages")
class MessageController {

    record SendRequest(String content) {
    }

    private final AssistantService assistant;
    private final AiAccessGuard access;

    MessageController(AssistantService assistant, AiAccessGuard access) {
        this.assistant = assistant;
        this.access = access;
    }

    /** Sends a question and returns the assistant's answer. */
    @PostMapping
    Message send(@PathVariable UUID orgId, @PathVariable UUID projectId, @PathVariable UUID conversationId,
                 @RequestBody SendRequest request, @AuthenticationPrincipal AuthenticatedUser user) {
        access.requireWrite(user.userId(), orgId, projectId);
        String content = request.content() == null ? "" : request.content().strip();
        if (content.isEmpty() || content.length() > AssistantService.MAX_QUESTION_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "The message must be between 1 and " + AssistantService.MAX_QUESTION_LENGTH + " characters.");
        }
        return assistant.ask(orgId, projectId, user.userId(), conversationId, content);
    }
}