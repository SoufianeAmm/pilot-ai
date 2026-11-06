package com.flowpilot.projects;

import java.util.UUID;

/**
 * Read-side and numbering operations on projects, for other modules (notably {@code tasks}).
 *
 * <p>These are the only project operations other modules may call besides {@link ProjectAccess}. They
 * never expose the internal {@code Project} entity — only primitives (the key, the next task number).
 */
public interface ProjectQueries {

    /**
     * Atomically reserves and returns the next task number for the project, incrementing the
     * per-project counter under a row lock so concurrent task creations never get the same number.
     * The number is the {@code n} in the human key "{@code <project.key>-<n>}" (e.g. "FP-42").
     *
     * @throws com.flowpilot.shared.web.ApiException 404 if the project does not exist in the org
     */
    int reserveNextTaskNumber(UUID orgId, UUID projectId);

    /** @return the project's human key (e.g. "FP"), for composing task keys. */
    String keyOf(UUID orgId, UUID projectId);

    /**
     * @return whether the milestone belongs to the given project (so a task in project A cannot use
     *         a milestone from project B in the same organization)
     */
    boolean milestoneExists(UUID orgId, UUID projectId, UUID milestoneId);
}
