package com.flowpilot.ai.internal.application;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.flowpilot.ai.internal.application.MessageView.StoredAnswer;
import com.flowpilot.ai.internal.application.MessageView.Verification;
import com.flowpilot.ai.internal.persistence.ConversationStore;
import com.flowpilot.ai.internal.persistence.ConversationStore.Conversation;
import com.flowpilot.ai.internal.persistence.ConversationStore.StoredMessage;
import com.flowpilot.projects.ProjectPermission;
import com.flowpilot.projects.ProjectScope;
import com.flowpilot.shared.web.ApiException;

import tools.jackson.databind.json.JsonMapper;

/** The user's own analyst conversations in a project: start, list, read, delete. */
@Service
public class ConversationService {

    static final int MAX_TITLE_LENGTH = 200;
    static final String DEFAULT_TITLE = "New conversation";
    private static final int MAX_LISTED = 100;

    private final ConversationStore store;
    private final JsonMapper json;

    ConversationService(ConversationStore store, JsonMapper json) {
        this.store = store;
        this.json = json;
    }

    public ConversationView create(ProjectScope scope, String requestedTitle) {
        scope.require(ProjectPermission.AI_ASK);
        String title = requestedTitle == null ? "" : requestedTitle.strip();
        if (title.length() > MAX_TITLE_LENGTH) {
            throw ApiException.badRequest("The title must be at most " + MAX_TITLE_LENGTH + " characters.");
        }
        return toView(store.create(scope, title.isEmpty() ? DEFAULT_TITLE : title));
    }

    public List<ConversationView> list(ProjectScope scope) {
        scope.require(ProjectPermission.AI_ASK);
        return store.list(scope, MAX_LISTED).stream().map(ConversationService::toView).toList();
    }

    public List<MessageView> messages(ProjectScope scope, UUID conversationId) {
        scope.require(ProjectPermission.AI_ASK);
        requireOwn(scope, conversationId);
        return store.messages(scope, conversationId).stream().map(this::toView).toList();
    }

    public void delete(ProjectScope scope, UUID conversationId) {
        scope.require(ProjectPermission.AI_ASK);
        if (!store.delete(scope, conversationId)) {
            throw notFound();
        }
    }

    /** 404 for a conversation that does not exist or belongs to someone else: the two look the same. */
    Conversation requireOwn(ProjectScope scope, UUID conversationId) {
        return store.find(scope, conversationId).orElseThrow(ConversationService::notFound);
    }

    MessageView toView(StoredMessage message) {
        if ("USER".equals(message.role())) {
            return new MessageView(message.id(), message.role(), message.content(), message.status(), null,
                    List.of(), null, null, message.createdAt());
        }
        StoredAnswer stored = message.structuredJson() == null ? null
                : json.readValue(message.structuredJson(), StoredAnswer.class);
        Verification verification = message.verificationJson() == null || "{}".equals(message.verificationJson().strip())
                ? null : json.readValue(message.verificationJson(), Verification.class);
        return new MessageView(message.id(), message.role(), message.content(), message.status(),
                stored == null ? null : stored.answer(),
                stored == null || stored.citations() == null ? List.of() : stored.citations(),
                verification, message.model(), message.createdAt());
    }

    private static ConversationView toView(Conversation c) {
        return new ConversationView(c.id(), c.projectId(), c.title(), c.createdAt(), c.updatedAt());
    }

    private static ApiException notFound() {
        return ApiException.notFound("Conversation not found.");
    }
}
