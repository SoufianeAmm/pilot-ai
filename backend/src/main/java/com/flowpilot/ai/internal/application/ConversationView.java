package com.flowpilot.ai.internal.application;

import java.time.Instant;
import java.util.UUID;

/** A conversation as the API returns it. */
public record ConversationView(UUID id, UUID projectId, String title, Instant createdAt, Instant updatedAt) {
}
