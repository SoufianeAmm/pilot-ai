package com.flowpilot.projects;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-only project summary for other modules (the AI analyst tools, analytics, notifications).
 * Callers must already hold a {@link ProjectScope} for the project.
 */
public record ProjectSummary(UUID id, UUID orgId, String key, String name, String status, Instant createdAt) {
}
