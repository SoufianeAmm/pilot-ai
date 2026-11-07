package com.flowpilot.projects.internal.domain;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite primary key of {@link ProjectMember}: a user is a member of a project at most once.
 * Required by JPA's {@code @IdClass} mapping because {@code project_member} has no surrogate id.
 */
public class ProjectMemberId implements Serializable {

    private UUID projectId;
    private UUID userId;

    public ProjectMemberId() {
    }

    public ProjectMemberId(UUID projectId, UUID userId) {
        this.projectId = projectId;
        this.userId = userId;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public UUID getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ProjectMemberId that)) {
            return false;
        }
        return Objects.equals(projectId, that.projectId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, userId);
    }
}
